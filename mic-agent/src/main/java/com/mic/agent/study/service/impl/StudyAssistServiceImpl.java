package com.mic.agent.study.service.impl;

import com.mic.agent.study.feign.InheritFeignClient;
import com.mic.agent.study.model.dto.ForumPostDTO;
import com.mic.agent.study.model.dto.ForumPostItemDTO;
import com.mic.agent.study.model.dto.ForumReplyItemDTO;
import com.mic.agent.study.model.dto.InheritItemDTO;
import com.mic.agent.study.service.StudyAssistService;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.vo.InheritSearchVO;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import dev.langchain4j.model.openai.OpenAiChatModel;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 传承与学习 Agent 业务服务实现（学习内容推荐 / 论坛辅助答疑 / 论坛智能总结）：
 * - 学习内容推荐：Feign 读取当前浏览条目 → 模型提取检索关键词 → 资料库检索候选 →
 *   剔除当前条目、按「视频学习 / 访谈资料优先，知识窗口补充」排序，最多返回 4 条；
 * - 论坛辅助答疑：Feign 读取帖子 → 关键词提取 → 资料库检索 → 模型按论坛助手角色生成回答草稿；
 * - 论坛智能总结：按搜索关键词读取命中帖子与帖内评论 → 模型概括讨论全貌（无命中返回空串）。
 * 关键词提取、草稿与总结生成均直接使用 ChatModelConfig 显式装配的 deepseekChatModel（非对话场景，无需记忆 / 工具）；
 * 被调服务统一 Result 包装：code 非 200 按业务失败处理（推荐 / 检索降级为空列表 / 空串，草稿与总结生成失败抛业务异常）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyAssistServiceImpl implements StudyAssistService {

    /** 推荐结果条数上限 */
    private static final int RECOMMEND_MAX = 4;

    /** 每次检索每类资料条数（被调接口按类别限流：访谈 / 知识 / 视频各 limit 条） */
    private static final int SEARCH_LIMIT = 3;

    /** 拼入提示词的正文截断长度（控制 token 消耗） */
    private static final int CONTENT_TRUNCATE = 400;

    /** 关键词提取结果最大长度（防御模型输出异常） */
    private static final int KEYWORDS_MAX_LENGTH = 60;

    /** 论坛智能总结：读取的帖子条数上限（按发布时间倒序取前若干条） */
    private static final int SUMMARY_POST_MAX = 4;

    /** 论坛智能总结：每帖读取的顶级评论条数 */
    private static final int SUMMARY_REPLY_MAX = 3;

    /** 论坛智能总结：单条评论截断长度（控制 token 消耗） */
    private static final int REPLY_TRUNCATE = 200;

    /** 论坛智能总结：跨服务读调用尝试次数（含首次）——Nacos 重连 / 负载均衡暂无实例等瞬时失败重试 1 次即可自愈，
     *  否则检索 503 被静默降级为空结果，前端总结区直接消失（表现为智能总结失效） */
    private static final int READ_MAX_ATTEMPTS = 2;

    /** 论坛智能总结：跨服务读调用重试间隔（毫秒，与 ShopTools.remoteCall 口径一致） */
    private static final long READ_RETRY_INTERVAL_MS = 300;

    private final InheritFeignClient inheritFeignClient;

    private final OpenAiChatModel deepseekChatModel;

    @Override
    public List<InheritSearchVO> recommend(Integer infoType, Long id) {
        // 1. 读取当前浏览条目（标题 + 正文/简介）作为推荐依据；条目不存在 / 服务异常时降级为空列表
        InheritItemDTO current;
        try {
            current = loadItem(infoType, id);
        } catch (Exception e) {
            log.warn("学习推荐：读取当前条目失败 infoType={}, id={}", infoType, id, e);
            return List.of();
        }
        if (current == null || !StringUtils.hasText(current.getTitle())) {
            return List.of();
        }
        // 2. 模型提取检索关键词，检索候选资料（检索失败返回空列表，由前端隐藏推荐区）
        String keywords = extractKeywords(current.getTitle(), current.getContent());
        List<InheritSearchVO> candidates = searchQuietly(keywords);
        // 3. 剔除当前条目本身
        List<InheritSearchVO> filtered = new ArrayList<>();
        for (InheritSearchVO item : candidates) {
            if (item == null || item.getId() == null || !StringUtils.hasText(item.getTitle())) {
                continue;
            }
            boolean isSelf = item.getId().equals(id) && Objects.equals(item.getInfoType(), infoType);
            if (!isSelf) {
                filtered.add(item);
            }
        }
        // 4. 「配套学习视频、访谈实录」优先（1视频学习、3访谈资料），知识窗口垫底补充；最多 4 条
        filtered.sort(Comparator.comparingInt((InheritSearchVO item) -> rank(item.getInfoType())));
        List<InheritSearchVO> result = filtered.size() > RECOMMEND_MAX
                ? new ArrayList<>(filtered.subList(0, RECOMMEND_MAX))
                : filtered;
        log.info("学习推荐完成：infoType={}, id={}, keywords={}, 结果 {} 条", infoType, id, keywords, result.size());
        return result;
    }

    @Override
    public String generateForumDraft(Long postId) {
        log.info("论坛草稿请求：收到生成请求 postId={}", postId);
        // 1. 读取帖子（仅前台可见帖子可生成草稿；不存在 / 未公开按参数错误提示）
        ForumPostDTO post = loadPost(postId);
        if (post == null || !StringUtils.hasText(post.getTitle())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "帖子不存在或暂不可见，无法生成回答草稿");
        }
        // 2. 关键词提取 + 知识库检索（检索失败不阻断草稿生成，模型按「资料有限」口径作答）
        String keywords = extractKeywords(post.getTitle(), post.getContent());
        List<InheritSearchVO> materials = searchQuietly(keywords);
        // 3. 生成回答草稿
        String draft = generateDraft(post, materials);
        if (!StringUtils.hasText(draft)) {
            throw new BusinessException(ResultCodeEnum.ERROR, "回答草稿生成失败，请稍后重试");
        }
        log.info("论坛草稿生成完成：postId={}, keywords={}, 资料 {} 条, 草稿 {} 字",
                postId, keywords, materials.size(), draft.length());
        return draft;
    }

    @Override
    public String summarizeForum(String keyword) {
        log.info("论坛智能总结请求：收到生成请求 keyword={}", keyword);
        // 1. 按关键词检索帖子（仅已通过帖子；无命中 / 检索失败返回空串，前端隐藏总结区）
        List<ForumPostItemDTO> posts = searchForumPosts(keyword);
        if (posts.isEmpty()) {
            log.warn("论坛智能总结：关键词「{}」未命中已通过帖子（前端将隐藏总结区；若前台论坛列表能搜到帖子，请检查 forumPage 返回）", keyword);
            return "";
        }
        // 2. 读取每帖顶级评论（二级回复随 VO 返回；单帖失败跳过，不影响其余帖子）
        StringBuilder context = new StringBuilder();
        for (ForumPostItemDTO post : posts) {
            appendPostContext(context, post, loadForumReplies(post.getId()));
        }
        // 3. 模型生成智能总结（失败抛业务异常，前端静默忽略不打扰搜索结果展示）
        String summary = generateSummary(keyword, context.toString());
        if (!StringUtils.hasText(summary)) {
            throw new BusinessException(ResultCodeEnum.ERROR, "智能总结生成失败，请稍后重试");
        }
        log.info("论坛智能总结完成：keyword={}, 命中帖子 {} 条, 总结 {} 字", keyword, posts.size(), summary.length());
        return summary;
    }

    /** Feign 检索论坛帖子（关键词命中前 SUMMARY_POST_MAX 条）：瞬时失败重试后仍异常 / 业务失败返回空列表 */
    private List<ForumPostItemDTO> searchForumPosts(String keyword) {
        try {
            Result<PageResult<ForumPostItemDTO>> result = readWithRetry("论坛帖子检索", "keyword=" + keyword,
                    () -> inheritFeignClient.forumPage(1, SUMMARY_POST_MAX, keyword));
            if (result == null || !ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())
                    || result.getData() == null || result.getData().getRecords() == null) {
                log.warn("论坛检索返回业务失败：keyword={}, result={}", keyword, result);
                return List.of();
            }
            return result.getData().getRecords();
        } catch (Exception e) {
            log.warn("论坛检索异常：keyword={}", keyword, e);
            return List.of();
        }
    }

    /** Feign 读取帖子顶级评论（每帖最多 SUMMARY_REPLY_MAX 条）：瞬时失败重试后仍异常 / 业务失败返回空列表 */
    private List<ForumReplyItemDTO> loadForumReplies(Long postId) {
        try {
            Result<PageResult<ForumReplyItemDTO>> result = readWithRetry("帖子评论读取", "postId=" + postId,
                    () -> inheritFeignClient.forumReplyPage(postId, 1, SUMMARY_REPLY_MAX));
            if (result == null || !ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())
                    || result.getData() == null || result.getData().getRecords() == null) {
                log.warn("帖子评论读取返回业务失败：postId={}, result={}", postId, result);
                return List.of();
            }
            return result.getData().getRecords();
        } catch (Exception e) {
            log.warn("帖子评论读取异常：postId={}", postId, e);
            return List.of();
        }
    }

    /**
     * 跨服务只读调用带瞬时失败重试（与 ShopTools.remoteCall 同口径）：
     * 无可用实例（负载均衡空缓存）/ 5xx / 网络抖动等瞬时失败 300ms 后重试 1 次；
     * 4xx 参数类错误重试无意义直接放弃；重试后仍失败抛出原始异常，由调用方按既有逻辑降级。
     */
    private <T> T readWithRetry(String action, String params, Supplier<T> caller) {
        Exception lastError = null;
        for (int attempt = 1; attempt <= READ_MAX_ATTEMPTS; attempt++) {
            try {
                return caller.get();
            } catch (Exception e) {
                lastError = e;
                if (e instanceof FeignException feignException) {
                    int status = feignException.status();
                    if (status >= 400 && status < 500) {
                        break; // 请求路径 / 参数不合法等客户端错误：重试无意义
                    }
                }
                if (attempt < READ_MAX_ATTEMPTS) {
                    log.warn("[论坛智能总结] 跨服务调用失败，{}ms 后重试 1 次（动作={}，参数={}，异常类型={}，异常信息={}）",
                            READ_RETRY_INTERVAL_MS, action, params, e.getClass().getSimpleName(), e.getMessage());
                    sleepQuietly(READ_RETRY_INTERVAL_MS);
                }
            }
        }
        if (lastError instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException(action + "失败", lastError);
    }

    /** 重试间隔等待：被中断时保留中断标记并放弃等待 */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 组装单帖上下文：标题（含评论数）/ 正文摘录 + 顶级评论与二级回复 */
    private void appendPostContext(StringBuilder context, ForumPostItemDTO post, List<ForumReplyItemDTO> replies) {
        context.append("【帖子】").append(StringUtils.hasText(post.getTitle()) ? post.getTitle() : "（无标题）");
        if (post.getReplyCount() != null) {
            context.append("（共 ").append(post.getReplyCount()).append(" 条评论）");
        }
        context.append("\n");
        if (StringUtils.hasText(post.getContent())) {
            context.append("正文：").append(truncate(post.getContent())).append("\n");
        }
        if (replies.isEmpty()) {
            context.append("评论：（暂无评论）\n\n");
            return;
        }
        context.append("评论：\n");
        for (ForumReplyItemDTO reply : replies) {
            appendReplyLine(context, reply, "  ");
        }
        context.append("\n");
    }

    /** 组装单条评论行（递归拼接二级回复；空内容跳过，每条按 REPLY_TRUNCATE 截断） */
    private void appendReplyLine(StringBuilder context, ForumReplyItemDTO reply, String indent) {
        if (reply == null || !StringUtils.hasText(reply.getReplyContent())) {
            return;
        }
        context.append(indent).append("- ")
                .append(StringUtils.hasText(reply.getNickname()) ? reply.getNickname() : "匿名用户")
                .append("：").append(truncateTo(reply.getReplyContent(), REPLY_TRUNCATE)).append("\n");
        if (reply.getReplies() == null) {
            return;
        }
        for (ForumReplyItemDTO child : reply.getReplies()) {
            appendReplyLine(context, child, indent + "  ");
        }
    }

    /** 模型生成论坛智能总结：注入检索命中的帖子与评论上下文，输出供搜索框下方展示的总结文本 */
    private String generateSummary(String keyword, String forumContext) {
        String prompt = """
                你是苗族非遗文化社区「苗韵遗珍」论坛的智能助手阿银。用户在论坛搜索了关键词「%s」，下面是检索到的帖子与帖内评论摘录：
                %s
                请为这次检索生成一段智能总结，帮助用户快速了解讨论全貌。
                要求：
                1. 200 字以内，先概括检索到的帖子讨论了什么（结合评论），再点出有代表性的观点或经验，可自然提及帖子标题；
                2. 评论稀少时如实概述帖子要点，不要虚构讨论内容；
                3. 只输出总结正文，不要标题、不要「总结：」等前缀。
                """.formatted(keyword, forumContext.strip());
        try {
            String summary = deepseekChatModel.chat(prompt);
            return summary == null ? "" : summary.strip();
        } catch (Exception e) {
            log.error("论坛智能总结生成失败", e);
            return "";
        }
    }

    /** 文本纯文本化并截断到指定长度（剥离 HTML 标签、压缩空白；空内容返回空串） */
    private String truncateTo(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String plain = content.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
        return plain.length() > maxLength ? plain.substring(0, maxLength) + "…" : plain;
    }

    /** Feign 读取传承信息详情：code 非 200（不存在 / 未发布）返回 null */
    private InheritItemDTO loadItem(Integer infoType, Long id) {
        Result<InheritItemDTO> result = inheritFeignClient.detail(id, infoType);
        return result != null && ResultCodeEnum.SUCCESS.getCode().equals(result.getCode()) ? result.getData() : null;
    }

    /** Feign 读取论坛帖子详情：code 非 200（不存在 / 未公开）返回 null */
    private ForumPostDTO loadPost(Long postId) {
        Result<ForumPostDTO> result = inheritFeignClient.forumDetail(postId);
        return result != null && ResultCodeEnum.SUCCESS.getCode().equals(result.getCode()) ? result.getData() : null;
    }

    /** 模型提取检索关键词（失败 / 输出为空时回退为按标题检索）；输出压缩为单行并限长，做防御性清洗 */
    private String extractKeywords(String title, String content) {
        String prompt = """
                你服务于苗族非遗文化资料库。请从下面的标题与内容中提取 2~4 个用于检索资料库的中文关键词（优先苗绣、蜡染、银饰、纹样、工艺、习俗等主题词），多个关键词用空格分隔，只输出关键词、不要任何解释。
                标题：%s
                内容：%s""".formatted(title, truncate(content));
        String fallback = fallbackKeywords(title);
        try {
            String keywords = cleanKeywords(deepseekChatModel.chat(prompt));
            return StringUtils.hasText(keywords) ? keywords : fallback;
        } catch (Exception e) {
            log.warn("关键词提取失败，回退按标题检索：title={}", title, e);
            return fallback;
        }
    }

    /** 模型生成论坛回答草稿：注入帖子与知识库资料，要求输出可直接发布的回答正文 */
    private String generateDraft(ForumPostDTO post, List<InheritSearchVO> materials) {
        StringBuilder materialsText = new StringBuilder();
        if (materials.isEmpty()) {
            materialsText.append("（知识库中未检索到与该提问直接相关的资料）");
        } else {
            for (InheritSearchVO item : materials) {
                materialsText.append("【").append(typeLabel(item.getInfoType())).append("】《")
                        .append(item.getTitle()).append("》\n")
                        .append(StringUtils.hasText(item.getSnippet()) ? item.getSnippet() : "")
                        .append("\n\n");
            }
        }
        String prompt = """
                你是苗族非遗文化社区「苗韵遗珍」论坛的智能助手阿银。请根据【知识库资料】为用户在论坛发布的提问撰写一段可以直接发布的回答草稿。
                要求：
                1. 语气友好、专业、简洁，300 字以内，条理清晰（可分点）；
                2. 优先使用【知识库资料】中的信息作答，可自然标注资料来源板块（如「知识窗口」）；
                3. 若资料不足或与提问无关，基于非遗常识谨慎作答，并注明「资料库信息有限，仅供参考」；
                4. 只输出回答正文，不要标题、不要「草稿：」等前缀。
                【论坛提问】
                标题：%s
                正文：%s
                【知识库资料】
                %s""".formatted(post.getTitle(), truncate(post.getContent()), materialsText.toString().strip());
        try {
            String draft = deepseekChatModel.chat(prompt);
            return draft == null ? "" : draft.strip();
        } catch (Exception e) {
            log.error("回答草稿生成失败", e);
            return "";
        }
    }

    /** 检索资料（推荐 / 草稿共用）：业务失败或异常时返回空列表，不阻断主流程 */
    private List<InheritSearchVO> searchQuietly(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return List.of();
        }
        try {
            Result<List<InheritSearchVO>> result = inheritFeignClient.search(keywords, SEARCH_LIMIT);
            if (result == null || !ResultCodeEnum.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
                log.warn("资料检索返回业务失败：keywords={}, result={}", keywords, result);
                return List.of();
            }
            return result.getData();
        } catch (Exception e) {
            log.warn("资料检索异常：keywords={}", keywords, e);
            return List.of();
        }
    }

    /** 关键词回退策略：去除书名号 / 标点等修饰字符后按标题检索 */
    private String fallbackKeywords(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        String cleaned = title.replaceAll("[《》「」【】（）()“”\"'：:；;，,。.!！?？|·—\\-\\s]+", " ").strip();
        return cleaned.length() > 20 ? cleaned.substring(0, 20) : cleaned;
    }

    /** 压缩模型输出为单行关键词：去换行 / 引号 / 结尾标点并限长 */
    private String cleanKeywords(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String cleaned = raw.replaceAll("[\\r\\n《》「」【】“”\"'：:]", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("[。，、；;]+$", "")
                .strip();
        return cleaned.length() > KEYWORDS_MAX_LENGTH ? cleaned.substring(0, KEYWORDS_MAX_LENGTH) : cleaned;
    }

    /** 正文纯文本化并截断（拼入提示词用：剥离 HTML 标签、压缩空白） */
    private String truncate(String content) {
        if (!StringUtils.hasText(content)) {
            return "（无）";
        }
        String plain = content.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
        return plain.length() > CONTENT_TRUNCATE ? plain.substring(0, CONTENT_TRUNCATE) + "…" : plain;
    }

    /** 推荐排序权重：学习视频 / 访谈实录优先（需求「推荐配套学习视频、访谈实录」），知识窗口垫底 */
    private int rank(Integer infoType) {
        if (Objects.equals(1, infoType)) {
            return 0;
        }
        if (Objects.equals(3, infoType)) {
            return 1;
        }
        if (Objects.equals(4, infoType)) {
            return 2;
        }
        return 3;
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
