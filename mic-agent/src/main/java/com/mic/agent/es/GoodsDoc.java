package com.mic.agent.es;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品检索文档：ES 索引 mic_goods 中的一条商品记录。
 * 字段与 intangible_goods 表（见 mic.sql）对齐，只保留检索与问答需要的字段；
 * 字段名即 ES 文档的 JSON 属性名（JacksonJsonpMapper 按字段名序列化 / 反序列化）。
 */
@Data
public class GoodsDoc {

    /** 商品ID（写入 ES 时作为文档 _id，重复同步时按主键覆盖，保证幂等） */
    private Long id;

    /** 商品分类ID */
    private Long categoryId;

    /** 商品名称（mapping 为 text：短语匹配检索字段） */
    private String goodsName;

    /** 商品描述（mapping 为 text：全文匹配检索字段） */
    private String goodsDesc;

    /** 商品价格 */
    private BigDecimal price;

    /** 库存 */
    private Integer stock;
}
