package com.mic.agent.controller;

import com.mic.agent.es.GoodsEsIndexService;
import com.mic.agent.es.KnowledgeEsIndexService;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ES 索引维护接口：
 * 服务启动时 GoodsEsIndexService（商品）/ KnowledgeEsIndexService（非遗知识库）已各自自动完成一次全量重建；
 * 数据变更后如需立即生效，可手动调用本接口重建对应索引——
 * 商品数据（上架 / 下架 / 改价）→ /rebuild；RAG 知识库文档（resources/rag 增改）→ /knowledge/rebuild。
 */
@Tag(name = "ES索引维护接口")
@RestController
@RequestMapping("/api/agent/es")
@RequiredArgsConstructor
public class EsIndexController {

    private final GoodsEsIndexService goodsEsIndexService;

    /** 知识库索引服务为可选依赖：agent.rag.enabled=false 时不装配（RAG 关闭时无知识库索引可重建） */
    private final ObjectProvider<KnowledgeEsIndexService> knowledgeEsIndexServiceProvider;

    @Operation(summary = "重建商品索引：删除旧索引 → 按映射重建 → 从 MySQL 全量同步已上架商品，返回同步条数")
    @PostMapping("/rebuild")
    public Result<Integer> rebuild() {
        return Result.ok(goodsEsIndexService.rebuildIndex());
    }

    @Operation(summary = "重建非遗知识库索引：删除旧索引 → 按映射重建 → RAG 知识库文档（resources/rag）重新切分 / 向量化入库，返回入库片段数")
    @PostMapping("/knowledge/rebuild")
    public Result<Integer> rebuildKnowledge() {
        KnowledgeEsIndexService service = knowledgeEsIndexServiceProvider.getIfAvailable();
        if (service == null) {
            return Result.fail(ResultCodeEnum.ERROR, "RAG 知识库未启用（agent.rag.enabled=false），无需重建知识库索引");
        }
        return Result.ok(service.rebuildIndex());
    }
}
