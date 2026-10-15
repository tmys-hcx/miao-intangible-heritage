package com.mic.goods.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单商品明细VO（下单时商品快照）
 * 说明：订单列表 / 详情 / 卖家订单共用；商品后续被修改或删除不影响展示
 */
@Data
public class OrderGoodsItemVO implements Serializable {

    /** 商品ID */
    private Long goodsId;

    /** 商品名称（下单时快照） */
    private String goodsName;

    /** 商品图片，JSON数组（下单时快照） */
    private String goodsImg;

    /** 商品单价（下单时快照） */
    private BigDecimal price;

    /** 购买数量 */
    private Integer goodsNum;
}
