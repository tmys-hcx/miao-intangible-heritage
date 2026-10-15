package com.mic.agent.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * 商品 ES 索引同步服务（索引生命周期管理）：
 *
 * 同步策略（知识重点）：
 * 1、应用启动时全量重建：删除旧索引 → 按 mapping 重建 → bulk 批量写入 → refresh 刷新；
 *    实训数据量小，全量重建最简单可靠、结果确定（生产环境应改为增量同步：Canal 订阅 binlog 或 Logstash 定时增量）；
 * 2、数据来源直查 MySQL：mic-agent 与 mic-goods 共用同一数据库 miao_intangible_heritage，
 *    用 JdbcTemplate 直接查 intangible_goods 表一次拉全量，省去 Feign 循环分页的多次网络往返；
 * 3、只同步"已上架未删除"商品（status=1 AND is_deleted=0），与前台可见商品口径一致。
 *
 * 启动同步失败（ES 未启动等）不阻断应用启动，仅告警；可调用 POST /api/agent/es/rebuild 手动重建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsEsIndexService implements ApplicationRunner {

    private final ElasticsearchClient esClient;

    private final JdbcTemplate jdbcTemplate;

    /** 商品检索索引名（application.yml 的 elasticsearch.index，与检索节点共用同一配置项） */
    @Value("${elasticsearch.index:mic_goods}")
    private String indexName;

    /** 应用启动后自动执行一次全量同步（实现 ApplicationRunner 接口） */
    @Override
    public void run(ApplicationArguments args) {
        try {
            int count = rebuildIndex();
            log.info("商品ES索引初始化完成：索引[{}]共同步{}条已上架商品", indexName, count);
        } catch (Exception e) {
            // ES 未启动 / 网络不通：不阻断应用启动（检索节点有兜底），记录告警供排查
            log.warn("商品ES索引初始化失败（不影响服务启动，可调用 POST /api/agent/es/rebuild 重试）：{}", e.getMessage());
        }
    }

    /**
     * 全量重建索引：删除旧索引 → 创建索引与字段映射 → 从 MySQL 读取已上架商品 → bulk 批量写入 → refresh
     *
     * @return 本次同步的商品条数
     */
    public int rebuildIndex() {
        deleteIndexIfExists();
        createIndex();
        List<GoodsDoc> goodsList = loadOnSaleGoods();
        bulkIndex(goodsList);
        refreshIndex();
        return goodsList.size();
    }

    /** 删除旧索引（首次启动索引不存在属正常情况，忽略 index_not_found_exception） */
    private void deleteIndexIfExists() {
        try {
            esClient.indices().delete(d -> d.index(indexName));
            log.info("旧索引[{}]已删除，准备重建", indexName);
        } catch (ElasticsearchException e) {
            log.info("旧索引[{}]不存在，跳过删除", indexName);
        } catch (IOException e) {
            throw new UncheckedIOException("删除索引失败：" + indexName, e);
        }
    }

    /**
     * 创建索引并声明字段映射（mapping）：
     * text 类型会分词，用于全文检索（goodsName / goodsDesc）；数值类型用于展示与排序（price / stock）。
     * mapping 是 ES 的"表结构"：显式声明可避免动态映射把商品名映射成 keyword（整体不可分词）导致检索失效。
     */
    private void createIndex() {
        try {
            esClient.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("id", p -> p.long_(l -> l))
                            .properties("categoryId", p -> p.long_(l -> l))
                            .properties("goodsName", p -> p.text(t -> t))
                            .properties("goodsDesc", p -> p.text(t -> t))
                            .properties("price", p -> p.double_(d -> d))
                            .properties("stock", p -> p.integer(i -> i))
                    ));
            log.info("索引[{}]创建完成（含字段映射）", indexName);
        } catch (IOException e) {
            throw new UncheckedIOException("创建索引失败：" + indexName, e);
        }
    }

    /** 从 MySQL 读取全部已上架未删除商品（列名下划线 → GoodsDoc 驼峰字段手工映射） */
    private List<GoodsDoc> loadOnSaleGoods() {
        return jdbcTemplate.query("""
                SELECT id, category_id, goods_name, goods_desc, price, stock
                FROM intangible_goods
                WHERE status = 1 AND is_deleted = 0
                """, (rs, rowNum) -> {
            GoodsDoc doc = new GoodsDoc();
            doc.setId(rs.getLong("id"));
            doc.setCategoryId(rs.getLong("category_id"));
            doc.setGoodsName(rs.getString("goods_name"));
            doc.setGoodsDesc(rs.getString("goods_desc"));
            doc.setPrice(rs.getBigDecimal("price"));
            doc.setStock(rs.getInt("stock"));
            return doc;
        });
    }

    /** bulk 批量写入：多个文档合并为一次请求（相比逐条 index 大幅减少网络往返）；文档 _id 用商品ID保证幂等 */
    private void bulkIndex(List<GoodsDoc> goodsList) {
        if (goodsList.isEmpty()) {
            log.info("没有已上架商品，跳过写入");
            return;
        }

        BulkRequest.Builder bulk = new BulkRequest.Builder();
        for (GoodsDoc doc : goodsList) {
            bulk.operations(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(String.valueOf(doc.getId()))
                    .document(doc)));
        }

        try {
            BulkResponse response = esClient.bulk(bulk.build());
            // bulk 整体成功不代表每条都成功：errors=true 时需逐条检查（单条失败不影响其他文档）
            if (response.errors()) {
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .limit(5)
                        .forEach(item -> log.error("商品文档写入失败：id={}，原因={}", item.id(), item.error().reason()));
                throw new IllegalStateException("部分商品文档写入 ES 失败，详见日志");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("商品文档批量写入失败：" + indexName, e);
        }
    }

    /** refresh 刷新索引：让刚写入的文档立即可被检索（否则默认要等约 1 秒的刷新周期，同步后马上查询会查不到） */
    private void refreshIndex() {
        try {
            esClient.indices().refresh(r -> r.index(indexName));
        } catch (IOException e) {
            throw new UncheckedIOException("刷新索引失败：" + indexName, e);
        }
    }
}
