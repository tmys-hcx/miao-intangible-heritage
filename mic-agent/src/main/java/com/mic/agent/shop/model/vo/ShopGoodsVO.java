package com.mic.agent.shop.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 非遗商品VO（Agent 侧契约类）：
 * 与 mic-goods 商品接口返回的 IntangibleGoods 字段对齐（Jackson 按字段名反序列化，未声明字段自动忽略），
 * 仅保留工具问答需要的字段，避免无用字段占用大模型上下文
 */
@Data
public class ShopGoodsVO implements Serializable {

    /** 商品ID（加购 / 详情 / 下单的商品编号） */
    private Long id;

    /** 商品分类ID */
    private Long categoryId;

    /** 商品名称 */
    private String goodsName;

    /** 商品描述 */
    private String goodsDesc;

    /** 商品图片地址 */
    private String goodsImg;

    /** 商品单价 */
    private BigDecimal price;

    /** 当前库存 */
    private Integer stock;

    /** 发货时间：付款后X天内发货 */
    private Integer deliveryTime;

    /** 单次限购数量 */
    private Integer limitBuyNum;

    /** 商品状态：0待审核 1上架（可购买） 2审核拒绝 3已下架 */
    private Integer status;
}
