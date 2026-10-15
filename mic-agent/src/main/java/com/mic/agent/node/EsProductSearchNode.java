package com.mic.agent.node;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.mic.agent.es.GoodsDoc;
import com.mic.agent.state.MicAgentState;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ES商品检索节点
 * 根据用户问题检索商品信息
 *
 * 运行方式（知识重点）：
 * 1、关键词抽取：先把用户口语化的问题交给大模型压缩成检索关键词（如"想买把好看的剪纸送人"→"剪纸"），
 *    避免整句丢给 ES 降低相关性；模型输出异常时退回原问题兜底（方法内部处理，不会中断）；
 * 2、ES 检索：bool 查询 goodsName 短语匹配（精确）+ goodsDesc 全文匹配（宽松），
 *    minimumShouldMatch=1 命中其一即可，避免漏召回；只取前 5 条控制大模型上下文长度；
 * 3、异常兜底：ES 未启动 / 索引未就绪时按"未命中"处理（esEmpty=true），
 *    保证整张状态图不受外部依赖影响，最终由答复节点向用户说明没有找到商品。
 */
@Slf4j
@Component
public class EsProductSearchNode {

    // 大模型：用于从自然语言问题中抽取检索关键词
    private final ChatModel chatModel;

    // ES 客户端（EsClientConfig 中装配）
    private final ElasticsearchClient esClient;

    /** 商品检索索引名（与 GoodsEsIndexService 共用 application.yml 的 elasticsearch.index 配置项） */
    @Value("${elasticsearch.index:mic_goods}")
    private String indexName;

    public EsProductSearchNode(ChatModel chatModel, ElasticsearchClient esClient) {
        this.chatModel = chatModel;
        this.esClient = esClient;
    }

    public Map<String, Object> apply(MicAgentState state) {
        String userQuestion = state.question();

        // ==========真实ES检索==========
        List<String> docs = new ArrayList<>();
        try {
            // 1. 关键词抽取（内部已兜底：模型调用失败或不输出内容时退回原问题，不会抛异常）
            String keywords = extractKeywords(userQuestion);

            // 2. ES 检索：短语匹配商品名 + 全文匹配商品描述，命中其一即可，最多取5条
            SearchResponse<GoodsDoc> response = esClient.search(s -> s
                            .index(indexName)
                            .size(5)
                            .query(q -> q.bool(b -> b
                                    .should(sh -> sh.matchPhrase(m -> m.field("goodsName").query(keywords)))
                                    .should(sh -> sh.match(m -> m.field("goodsDesc").query(keywords)))
                                    .minimumShouldMatch("1"))),
                    GoodsDoc.class);

            // 3. 命中文档格式化为"商品：xx，库存：xx，价格：xx元"文本，供答复节点组织话术
            for (Hit<GoodsDoc> hit : response.hits().hits()) {
                GoodsDoc goods = hit.source();
                if (goods == null) {
                    continue;
                }
                docs.add("商品：%s，库存：%s，价格：%s元".formatted(
                        goods.getGoodsName(),
                        goods.getStock() == null ? "未知" : goods.getStock(),
                        goods.getPrice() == null ? "未知" : goods.getPrice().stripTrailingZeros().toPlainString()));
            }
            log.info("ES商品检索：关键词=[{}]，命中{}条", keywords, docs.size());
        } catch (Exception e) {
            // 兜底：ES 不可用时按"没查到商品"继续执行图（esEmpty=true），不让外部依赖中断编排
            log.warn("ES商品检索失败，按未命中处理：{}", e.getMessage());
            docs.clear();
        }
        boolean esEmpty = docs.isEmpty();

        // 写入状态：商品文档集合 + 是否为空标记
        return Map.of(
                "productDocs", docs,
                "esEmpty", esEmpty
        );
    }

    /** 用大模型从用户问题中抽取检索关键词（输出压缩成一行；解析异常或输出异常时用原问题兜底） */
    private String extractKeywords(String question) {
        String prompt = """
                你是商品搜索关键词抽取助手。请从用户问题中提取最能代表其想查找商品的核心关键词（品类 / 材质 / 用途等）。
                只输出关键词本身，多个关键词用空格分隔，不要输出任何解释、标点或多余文字。

                用户问题：%s
                """.formatted(question);
        try {
            String keywords = chatModel.chat(prompt)
                    .replaceAll("[\\r\\n\"'`]", " ") // 清理模型偶发输出的换行与引号
                    .trim();
            // 模型没抽出内容、或把整句问题抄回来（过长）时，退回原问题交给 ES 分词匹配
            return keywords.isEmpty() || keywords.length() > 50 ? question : keywords;
        } catch (Exception e) {
            log.warn("检索关键词抽取失败，使用原问题检索：{}", e.getMessage());
            return question;
        }
    }
}
