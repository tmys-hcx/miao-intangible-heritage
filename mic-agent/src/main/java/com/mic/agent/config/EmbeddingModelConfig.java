package com.mic.agent.config;

import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量模型配置（RAG 检索增强，显式装配，与 ChatModelConfig 同款路线）：
 * 按 application.yml 的 langchain4j.open-ai.embedding-model.* 配置创建 EmbeddingModel Bean——
 * OpenAI 兼容协议指向阿里云百炼（DashScope）text-embedding-v4，供知识库索引同步
 * （KnowledgeEsIndexService 文档向量化入库）与向量检索（EsKnowledgeContentRetriever 查询向量化）共用。
 *
 * 为什么显式装配：dev.langchain4j.openai.spring.AutoConfig 已在 application.yml 中被
 * spring.autoconfigure.exclude 排除（其模型 Bean 无 @ConditionalOnMissingBean 退让，曾致启动冲突），
 * 故对话模型与向量模型统一走显式装配，参数由本类读取，装配结果完全确定、可控。
 *
 * 启用条件：agent.rag.enabled=true（与 RagConfig 一致）——关闭 RAG 时本配置不加载，
 * 无 embedding 配置的环境也不会因缺少向量模型配置而启动失败。
 * HTTP 客户端与对话模型保持一致：显式 SpringRestClient，避免 SPI 默认选择的不确定性；
 * 批量上限限制为 10：DashScope text-embedding-v4 单次请求文本数上限，超限直接 400。
 */
@Configuration
@ConditionalOnProperty(prefix = "agent.rag", name = "enabled", havingValue = "true")
public class EmbeddingModelConfig {

    @Value("${langchain4j.open-ai.embedding-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.embedding-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.embedding-model.log-requests:false}")
    private boolean logRequests;

    @Value("${langchain4j.open-ai.embedding-model.log-responses:false}")
    private boolean logResponses;

    /** 向量模型：RagConfig 注入使用（Bean 类型 EmbeddingModel，与 @AiService / 编程式装配无耦合） */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .httpClientBuilder(SpringRestClient.builder())
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                // DashScope 限制单次 embedding 请求最多 10 条文本（超限报 400 batch size is invalid）：
                // 启动入库时 KnowledgeEsIndexService 会一次性提交全部文档片段，须限制批量大小使其自动分批
                .maxSegmentsPerBatch(10)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
