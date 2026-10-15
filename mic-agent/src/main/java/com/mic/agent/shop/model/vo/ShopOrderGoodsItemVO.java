package com.mic.agent.shop.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单商品明细VO（Agent 侧契约类）：
 * 与 mic-goods 订单接口返回的 OrderGoodsItemVO 字段对齐（下单时商品快照）
 */
@Data
public class ShopOrderGoodsItemVO implements Serializable {

    /** 商品ID */
    private Long goodsId;

    /** 商品名称（下单时快照） */
    private String goodsName;

    /** 商品单价（下单时快照） */
    private BigDecimal price;

    /** 购买数量 */
    private Integer goodsNum;
}
