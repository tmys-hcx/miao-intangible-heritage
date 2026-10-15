package com.mic.agent.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 非遗知识库 ES 索引同步服务（索引生命周期管理，替代原内存向量库 InMemoryEmbeddingStore）：
 *
 * 学习助手（study agent）的 RAG 非遗知识库文档（resources/rag）原方案在启动时向量化后只存于进程内存：
 * 重启即丢、每次启动都要重新调用向量模型、无法与其他 ES 数据统一观测；现改为写入 ES 向量索引持久化——
 * 索引 mic_study_knowledge（配置见 application.yml 的 elasticsearch.knowledge-index）：
 * text 存片段原文（text 类型分词，可兼做全文检索），embedding 为 dense_vector（index=true + cosine）承载 kNN 相似检索。
 *
 * 同步策略（知识重点，与商品索引 GoodsEsIndexService 同款"启动全量重建"模式）：
 * 1、先完成"加载 → 切分 → 向量化"，成功后才删除旧索引重建：向量模型 / Embedding API 异常时旧索引继续可用；
 * 2、删除旧索引 → 按 mapping 重建：dense_vector 的 dims 动态取向量模型实际输出维度，换模型 / 调维度后无需改代码；
 * 3、bulk 批量写入（EmbeddingModel 已按 DashScope 单次 10 条上限自动分批向量化）→ refresh 让片段立即可被检索；
 * 4、mapping 必须显式声明 embedding 为 dense_vector，否则动态映射会把向量数组当普通数值字段，kNN 检索失效。
 *
 * 启用条件：agent.rag.enabled=true（与 RagConfig / EmbeddingModelConfig 一致，关闭 RAG 时无知识库索引）；
 * 启动同步失败（ES 未启动 / 向量模型不可用等）不阻断应用启动，仅告警；
 * 增改 resources/rag 文档后可调用 POST /api/agent/es/knowledge/rebuild 手动重建，无需重启服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent.rag", name = "enabled", havingValue = "true")
public class KnowledgeEsIndexService implements ApplicationRunner {

    /** RAG 知识库文档目录（classpath 下，随应用打包） */
    private static final String RAG_DOC_PATH = "rag";

    /** 单个知识片段最大字符数（片段过大相似度被稀释，过小语义不完整） */
    private static final int SEGMENT_MAX_CHARS = 400;

    /** 相邻片段重叠字符数（重叠可避免关键句被切在片段边界、跨段语义丢失） */
    private static final int SEGMENT_OVERLAP_CHARS = 80;

    private final ElasticsearchClient esClient;

    /** 向量模型（EmbeddingModelConfig 装配的 text-embedding-v4，入库向量化用） */
    private final EmbeddingModel embeddingModel;

    /** 知识库向量检索索引名（application.yml 的 elasticsearch.knowledge-index，与 EsKnowledgeContentRetriever 共用同一配置项） */
    @Value("${elasticsearch.knowledge-index:mic_study_knowledge}")
    private String indexName;

    /** 应用启动后自动执行一次全量重建（实现 ApplicationRunner 接口） */
    @Override
    public void run(ApplicationArguments args) {
        try {
            int count = rebuildIndex();
            log.info("非遗知识库ES索引初始化完成：索引[{}]共入库{}个知识片段", indexName, count);
        } catch (Exception e) {
            // ES 未启动 / 向量模型不可用：不阻断应用启动（检索器侧有异常降级），记录告警供排查
            log.warn("非遗知识库ES索引初始化失败（不影响服务启动，可调用 POST /api/agent/es/knowledge/rebuild 重试）：{}", e.getMessage());
        }
    }

    /**
     * 全量重建索引：加载知识库文档 → 切分 → 向量化 → 删除旧索引 → 创建索引与字段映射 → bulk 写入 → refresh
     *
     * @return 本次入库的知识片段数
     */
    public int rebuildIndex() {
        // 1. 先完成"加载 → 切分 → 向量化"，任一步失败都不会破坏旧索引（旧索引继续对外提供检索）
        List<TextSegment> segments = loadAndSplitDocuments();
        List<Embedding> embeddings = embedSegments(segments);
        // 2. dense_vector 维度直接取向量模型实际输出维度（text-embedding-v4 当前输出 1024 维），
        //    保证 mapping 与向量空间严格对齐——维度不符时写入 / 检索都会被 ES 拒绝
        int dims = embeddings.get(0).vector().length;
        // 3. 删除旧索引 → 按映射重建 → 写入新片段 → 刷新
        deleteIndexIfExists();
        createIndex(dims);
        bulkIndex(segments, embeddings);
        refreshIndex();
        return segments.size();
    }

    /** 加载 classpath:rag 下全部知识库文档并切分为知识片段（单段约 400 字符、相邻段重叠 80 字符，保持语义连续） */
    private List<TextSegment> loadAndSplitDocuments() {
        List<Document> documents = ClassPathDocumentLoader.loadDocuments(RAG_DOC_PATH);
        // 切分器（recursive 递归切分：优先按段落 / 句子边界切，超长再按字符切），var 由 DocumentSplitters 返回类型推断
        var splitter = DocumentSplitters.recursive(SEGMENT_MAX_CHARS, SEGMENT_OVERLAP_CHARS);
        List<TextSegment> segments = new ArrayList<>();
        for (Document document : documents) {
            segments.addAll(splitter.split(document));
        }
        if (segments.isEmpty()) {
            throw new IllegalStateException("RAG 知识库文档为空：" + RAG_DOC_PATH);
        }
        return segments;
    }

    /** 向量化全部片段（EmbeddingModel 已按 maxSegmentsPerBatch=10 自动分批，规避 DashScope 单请求文本数上限） */
    private List<Embedding> embedSegments(List<TextSegment> segments) {
        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        List<Embedding> embeddings = response.content();
        // 向量与片段必须一一对应（按顺序配对写入），数量不一致说明向量化返回异常，中止重建
        if (embeddings == null || embeddings.size() != segments.size()) {
            throw new IllegalStateException("向量化结果数量与知识片段不一致：片段 " + segments.size()
                    + " 个，向量 " + (embeddings == null ? 0 : embeddings.size()) + " 条");
        }
        return embeddings;
    }

    /** 删除旧索引（首次启动索引不存在属正常情况，忽略 index_not_found_exception） */
    private void deleteIndexIfExists() {
        try {
            esClient.indices().delete(d -> d.index(indexName));
            log.info("旧知识库索引[{}]已删除，准备重建", indexName);
        } catch (ElasticsearchException e) {
            log.info("旧知识库索引[{}]不存在，跳过删除", indexName);
        } catch (IOException e) {
            throw new UncheckedIOException("删除索引失败：" + indexName, e);
        }
    }

    /**
     * 创建索引并声明字段映射（mapping）：
     * embedding 声明为 dense_vector（index=true + cosine 相似度），kNN 检索依赖此声明；
     * dims 为向量模型实际输出维度，必须严格一致（维度不符时写入 / 检索都会被 ES 拒绝）。
     */
    private void createIndex(int dims) {
        try {
            esClient.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("chunkId", p -> p.keyword(k -> k))
                            .properties("sourceFile", p -> p.keyword(k -> k))
                            .properties("text", p -> p.text(t -> t))
                            .properties("embedding", p -> p.denseVector(dv -> dv
                                    .dims(dims)
                                    .index(true)
                                    .similarity(DenseVectorSimilarity.Cosine))
                            )));
            log.info("知识库索引[{}]创建完成（含 dense_vector 字段映射，dims={}）", indexName, dims);
        } catch (IOException e) {
            throw new UncheckedIOException("创建索引失败：" + indexName, e);
        }
    }

    /** bulk 批量写入：片段与向量按序一一对应；文档 _id 用"来源文件#序号"，可读且重复重建幂等 */
    private void bulkIndex(List<TextSegment> segments, List<Embedding> embeddings) {
        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            // 来源文件名由 ClassPathDocumentLoader 写入文档元数据（Document.FILE_NAME = file_name），切分后保留在片段元数据
            String sourceFile = segment.metadata() == null ? null : segment.metadata().getString(Document.FILE_NAME);
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setChunkId(sourceFile + "#" + i);
            doc.setSourceFile(sourceFile);
            doc.setText(segment.text());
            doc.setEmbedding(toFloatList(embeddings.get(i).vector()));
            bulk.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(doc.getChunkId())
                    .document(doc)));
        }

        try {
            BulkResponse response = esClient.bulk(bulk.build());
            // bulk 整体成功不代表每条都成功：errors=true 时需逐条检查（单条失败不影响其他文档）
            if (response.errors()) {
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .limit(5)
                        .forEach(item -> log.error("知识库片段写入失败：id={}，原因={}", item.id(), item.error().reason()));
                throw new IllegalStateException("部分知识库片段写入 ES 失败，详见日志");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("知识库片段批量写入失败：" + indexName, e);
        }
    }

    /** refresh 刷新索引：让刚写入的片段立即可被检索（否则默认要等约 1 秒的刷新周期） */
    private void refreshIndex() {
        try {
            esClient.indices().refresh(r -> r.index(indexName));
        } catch (IOException e) {
            throw new UncheckedIOException("刷新索引失败：" + indexName, e);
        }
    }

    /** float[] → List&lt;Float&gt;：向量模型输出数组，ES 文档（Jackson 序列化）与 kNN 查询入参均为列表形式 */
    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }
}
