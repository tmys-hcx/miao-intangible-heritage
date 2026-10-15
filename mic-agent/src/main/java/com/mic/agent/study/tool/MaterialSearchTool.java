package com.mic.agent.study.tool;

import com.mic.agent.study.feign.InheritFeignClient;
import com.mic.common.model.vo.InheritSearchVO;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 非遗资料检索工具（Agent 工具调用，「资料检索问答」的数据来源）：
 * 模型在回答苗绣、蜡染、银饰等知识问题时调用本工具，双通道混合检索后整理为模型可读纯文本——
 * 通道①（关键词）：经 Feign 调 mic-inherit-reserve 聚合检索【访谈资料 / 知识窗口 / 学习视频简介】（仅已审核视频），每次三类资料各最多 3 条；
 * 通道②（向量语义）：经 ContentRetriever（ES kNN 实现）在非遗知识库向量索引（mic_study_knowledge，
 *   resources/rag 文档启动时由 KnowledgeEsIndexService 向量化入库）中做语义相似检索；
 * 结果为空或服务异常时返回明确文本，由系统提示词约束模型「如实告知无相关内容 / 服务暂不可用」，避免编造。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialSearchTool {

    /** 单次检索每类资料返回条数上限（访谈 / 知识 / 视频各 limit 条） */
    private static final int SEARCH_LIMIT = 3;

    /** RAG 语义片段单条截断长度：向量库切分片段可能较长，超长截断防止工具结果膨胀 */
    private static final int RAG_SNIPPET_MAX = 600;

    private final InheritFeignClient inheritFeignClient;

    /** 向量语义检索器（可选依赖：agent.rag.enabled=false 时容器无 ContentRetriever Bean，getIfAvailable 返回 null 自动降级为仅关键词检索） */
    private final ObjectProvider<ContentRetriever> contentRetrieverProvider;

    @Tool(name = "searchMaterials",
            value = "检索非遗资料库（平台访谈资料 / 知识窗口 / 学习视频简介的关键词检索，以及非遗知识库文档的向量语义检索，双通道混合召回），当用户询问苗绣、蜡染、银饰等非遗文化知识时调用")
    public String searchMaterials(@P("检索关键词，多个关键词用空格分隔，例如：苗绣 针法") String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "未提供检索关键词，请先明确要查询的非遗主题";
        }
        String trimmed = keyword.trim();
        // 通道①：平台资料库关键词检索（mic-inherit-reserve，多词 LIKE 匹配）；
        // 失败时 materials 保持 null（区别于“检索成功但无结果”的空列表），供下方区分“服务不可用”与“无结果”两种提示
        List<InheritSearchVO> materials = null;
        try {
            Result<List<InheritSearchVO>> result = inheritFeignClient.search(trimmed, SEARCH_LIMIT);
            if (result != null && ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
                materials = result.getData();
            } else {
                log.warn("资料检索返回业务失败：keyword={}, result={}", trimmed, result);
            }
        } catch (Exception e) {
            // 工具内兜底：Feign 网络异常等不让模型调用链崩溃，转为可读文本由模型自行处理；
            // 日志首行带异常摘要（如 FeignException$NotFound 404 / ConnectException 端口不通 / 超时），便于快速定位跨服务调用失败原因
            log.error("资料检索失败：keyword={}, 异常摘要={}", trimmed, e.toString(), e);
        }
        // 通道②：RAG 向量语义检索（ES 知识库向量索引 kNN 语义召回）；RAG 关闭或检索异常时返回空列表，自动降级
        List<Content> ragContents = ragRetrieve(trimmed);
        if (materials == null && ragContents.isEmpty()) {
            return "资料检索服务暂时不可用，请稍后重试";
        }
        if (materials != null && materials.isEmpty() && ragContents.isEmpty()) {
            return "未检索到与「" + trimmed + "」相关的非遗资料";
        }
        return formatMaterials(trimmed, materials, ragContents);
    }

    /** RAG 向量语义检索：ContentRetriever 未装配（agent.rag.enabled=false）或检索异常时返回空列表，由调用方降级处理 */
    private List<Content> ragRetrieve(String query) {
        ContentRetriever retriever = contentRetrieverProvider.getIfAvailable();
        if (retriever == null) {
            return List.of();
        }
        try {
            return retriever.retrieve(Query.from(query));
        } catch (Exception e) {
            log.warn("RAG 向量检索失败：keyword={}, 异常摘要={}", query, e.toString());
            return List.of();
        }
    }

    /** 混合检索结果 → 模型可读文本：先平台资料库（逐条标注来源板块与标题），再知识库文档语义片段（标注来源文档名）；materials 为 null 表示平台资料库暂不可用 */
    private String formatMaterials(String keyword, List<InheritSearchVO> materials, List<Content> ragContents) {
        // RAG 片段预处理：过滤空片段、标注来源文档名、超长截断
        List<String[]> ragSnippets = new ArrayList<>(); //[来源文档, 片段文本]
        for (Content content : ragContents) {
            if (content == null || content.textSegment() == null || !StringUtils.hasText(content.textSegment().text())) {
                continue;
            }
            String text = content.textSegment().text().trim();
            String source = content.textSegment().metadata() == null ? null
                    : content.textSegment().metadata().getString("file_name");
            if (text.length() > RAG_SNIPPET_MAX) {
                text = text.substring(0, RAG_SNIPPET_MAX) + "……";
            }
            ragSnippets.add(new String[]{source, text});
        }
        int platformCount = materials == null ? 0 : materials.size();
        StringBuilder sb = new StringBuilder("检索「").append(keyword).append("」共获得 ")
                .append(platformCount + ragSnippets.size()).append(" 条资料：\n");
        if (materials == null && !ragSnippets.isEmpty()) {
            sb.append("（平台资料库暂时不可用，以下为非遗知识库文档的语义检索结果）\n");
        }
        if (materials != null) {
            for (InheritSearchVO item : materials) {
                sb.append("\n【").append(typeLabel(item.getInfoType())).append("】《")
                        .append(item.getTitle()).append("》\n");
                if (StringUtils.hasText(item.getSnippet())) {
                    sb.append(item.getSnippet()).append('\n');
                }
            }
        }
        for (String[] snippet : ragSnippets) {
            sb.append("\n【知识库文档").append(StringUtils.hasText(snippet[0]) ? "：" + snippet[0] : "").append("】\n");
            sb.append(snippet[1]).append('\n');
        }
        return sb.toString();
    }

    /** 信息类型 → 板块名（与前台命名一致）：1视频学习 3访谈资料 4知识窗口 */
    private String typeLabel(Integer infoType) {
        if (Objects.equals(1, infoType)) {
            return "视频学习";
        }
        if (Objects.equals(3, infoType)) {
            return "访谈资料";
        }
        if (Objects.equals(4, infoType)) {
            return "知识窗口";
        }
        return "传承资料";
    }
}
