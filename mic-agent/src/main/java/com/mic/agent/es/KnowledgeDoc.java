package com.mic.agent.es;

import lombok.Data;

import java.util.List;

/**
 * 非遗知识库文档：ES 向量索引 mic_study_knowledge 中的一条知识片段。
 * 由 KnowledgeEsIndexService 将 resources/rag 知识库文档（苗绣针法 / 蜡染工艺 / 苗族银饰等）
 * 切分、向量化后写入；检索由 EsKnowledgeContentRetriever 以 kNN 语义相似度完成。
 * 字段名即 ES 文档的 JSON 属性名（JacksonJsonpMapper 按字段名序列化 / 反序列化）。
 */
@Data
public class KnowledgeDoc {

    /** 片段ID（写入 ES 时作为文档 _id：来源文件名#全局序号，重复重建时覆盖写入保证幂等） */
    private String chunkId;

    /** 来源文档名（检索命中后标注「来自哪个知识库文档」，mapping 为 keyword 不分词） */
    private String sourceFile;

    /** 片段原文（mapping 为 text：保留原文供检索命中后回填给模型引用作答） */
    private String text;

    /** 片段向量（mapping 为 dense_vector + index:true + cosine，承载 kNN 相似检索；维度与向量模型输出一致） */
    private List<Float> embedding;
}
