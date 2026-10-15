package com.mic.agent.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 非遗知识库向量检索器（ContentRetriever 的 ES kNN 实现，替代原 EmbeddingStoreContentRetriever + 内存向量库）：
 * 检索流程——查询文本经同一向量模型（text-embedding-v4）向量化 → ES kNN 在 mic_study_knowledge 索引中
 * 取语义最相似的知识片段 → 过滤低相关度 → 组装为 LangChain4j Content（file_name 元数据标注来源文档，
 * 与 MaterialSearchTool 的读取约定一致），供学习助手 searchMaterials 工具的向量语义通道使用。
 *
 * 相似度口径（知识重点）：ES kNN 对 cosine 相似度的 _score 归一化为 (1 + cosine) / 2（范围 0~1，越大越相似），
 * 故 minScore=0.75 等价于原内存向量库方案的余弦阈值 0.5；命中不足阈值时返回更少条数甚至为空，由调用方按"无结果"处理。
 *
 * 异常策略：ES 未启动 / 索引未就绪 / 向量模型异常时直接抛出（不在此静默吞掉），
 * 由 MaterialSearchTool.ragRetrieve 统一捕获降级为"仅平台资料库关键词检索"，保证对话链路不中断。
 */
@Slf4j
public class EsKnowledgeContentRetriever implements ContentRetriever {

    /** 返回片段数上限（k 与 size 一致：kNN 取相似度前 k 条，size 控制最终返回条数） */
    private static final int MAX_RESULTS = 3;

    /** kNN 候选集大小：越大召回越准但计算越慢，取 50（远大于 k，小数据量下的经验值） */
    private static final int NUM_CANDIDATES = 50;

    /** 相似度阈值：ES 归一化得分（cosine → (1 + cosine) / 2），0.75 等价原余弦阈值 0.5 */
    private static final double MIN_SCORE = 0.75;

    private final EmbeddingModel embeddingModel;

    private final ElasticsearchClient esClient;

    /** 知识库向量检索索引名（与 KnowledgeEsIndexService 共用 application.yml 的 elasticsearch.knowledge-index 配置项） */
    private final String indexName;

    public EsKnowledgeContentRetriever(EmbeddingModel embeddingModel, ElasticsearchClient esClient, String indexName) {
        this.embeddingModel = embeddingModel;
        this.esClient = esClient;
        this.indexName = indexName;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (query == null || !StringUtils.hasText(query.text())) {
            return List.of();
        }
        try {
            // 1. 查询文本向量化（与入库共用同一向量模型，保证查询与文档在同一向量空间）
            Embedding queryEmbedding = embeddingModel.embed(query.text()).content();

            // 2. ES kNN 检索：embedding 字段（dense_vector + cosine）取最相似的 MAX_RESULTS 个片段，
            //    minScore 过滤低相关度噪声（如闲聊类问题与知识库无关时避免把无关片段塞给模型）
            SearchResponse<KnowledgeDoc> response = esClient.search(s -> s
                            .index(indexName)
                            .size(MAX_RESULTS)
                            .minScore(MIN_SCORE)
                            .knn(k -> k
                                    .field("embedding")
                                    .queryVector(toFloatList(queryEmbedding.vector()))
                                    .k(MAX_RESULTS)
                                    .numCandidates(NUM_CANDIDATES)),
                    KnowledgeDoc.class);

            // 3. 命中文档组装为 Content：保留片段原文与来源文档名（file_name 与 MaterialSearchTool 的读取键一致）
            List<Content> contents = new ArrayList<>();
            for (Hit<KnowledgeDoc> hit : response.hits().hits()) {
                KnowledgeDoc doc = hit.source();
                if (doc == null || !StringUtils.hasText(doc.getText())) {
                    continue;
                }
                Metadata metadata = new Metadata();
                metadata.put(Document.FILE_NAME, doc.getSourceFile() == null ? "" : doc.getSourceFile());
                contents.add(Content.from(TextSegment.from(doc.getText(), metadata)));
            }
            log.info("ES知识库向量检索：查询=[{}]，命中{}条", query.text(), contents.size());
            return contents;
        } catch (IOException e) {
            // 受检异常转非受检（ContentRetriever 接口未声明受检异常），由调用方统一捕获降级
            throw new UncheckedIOException("ES知识库向量检索失败：" + indexName, e);
        }
    }

    /** float[] → List&lt;Float&gt;：向量模型输出数组，ES Java Client 的 kNN queryVector 入参为列表形式 */
    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }
}
