package com.mic.goods.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车列表项VO：购物车条目（Redis 存储，商品ID即条目标识） + 关联商品展示信息
 * 说明：商品被删除时商品字段为null（前端据此提示“商品已失效”）；
 * 商品状态非1（上架）时前端提示“已下架”，不可结算
 */
@Data
public class CartItemVO implements Serializable {

    /** 商品ID（Redis 购物车以商品ID作为条目标识） */
    private Long goodsId;

    /** 商品数量 */
    private Integer goodsNum;

    /** 加入时间 */
    private LocalDateTime createTime;

    /** 商品名称（商品已删除时为null） */
    private String goodsName;

    /** 商品图片（商品已删除时为null） */
    private String goodsImg;

    /** 商品单价（商品已删除时为null） */
    private BigDecimal price;

    /** 商品简介（商品已删除时为null） */
    private String goodsDesc;

    /** 发货时间：付款后X天内发货（商品已删除时为null） */
    private Integer deliveryTime;

    /** 当前库存（商品已删除时为null） */
    private Integer stock;

    /** 限购数量（商品已删除时为null） */
    private Integer limitBuyNum;

    /** 商品状态：0待审核 1上架（可购买） 2审核拒绝（已下架）（商品已删除时为null） */
    private Integer status;
}
