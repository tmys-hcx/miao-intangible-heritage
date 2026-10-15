package com.mic.agent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.mic.agent.es.EsKnowledgeContentRetriever;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 检索增强配置类（agent.rag.enabled=true 时启用）：
 * 依赖——1) EmbeddingModelConfig 装配的向量模型（langchain4j.open-ai.embedding-model 配置，text-embedding-v4）；
 * 2) EsClientConfig 装配的 Elasticsearch Java Client（9.x）；
 * 3) resources/rag 目录下已放置非遗知识库文档（启动时由 KnowledgeEsIndexService 切分 / 向量化写入
 *    ES 向量索引 mic_study_knowledge，目录 / 文档缺失会导致索引为空，注意勿删除）。
 * 装配产物：EsKnowledgeContentRetriever（ES kNN 向量检索器）——
 * 被 MaterialSearchTool 注入使用（searchMaterials 工具的向量语义通道），与平台资料库关键词检索混合召回。
 *
 * 变更说明：原方案为 InMemoryEmbeddingStore 内存向量库（重启即丢、每次启动重新向量化），
 * 现改为 ES 持久化向量索引（dense_vector + kNN），与商品检索统一到同一套 ES 基础设施。
 */
@Configuration
@ConditionalOnProperty(prefix = "agent.rag", name = "enabled", havingValue = "true")
public class RagConfig {

    @Autowired
    EmbeddingModel embeddingModel; //注入向量模型（查询文本向量化）

    /** 知识库向量检索索引名（application.yml 的 elasticsearch.knowledge-index，与 KnowledgeEsIndexService 共用同一配置项） */
    @Value("${elasticsearch.knowledge-index:mic_study_knowledge}")
    private String knowledgeIndexName;

    /*
     构建向量检索对象（ES kNN 实现）：
     查询文本向量化后在 ES 知识库向量索引（mic_study_knowledge）中做 kNN 语义相似检索
     */
    @Bean
    public ContentRetriever contentRetriever(ElasticsearchClient esClient) {
        return new EsKnowledgeContentRetriever(embeddingModel, esClient, knowledgeIndexName);
    }
}
