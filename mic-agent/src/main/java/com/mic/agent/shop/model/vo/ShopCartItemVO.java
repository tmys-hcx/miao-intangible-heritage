package com.mic.agent.shop.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车条目VO（Agent 侧契约类）：
 * 与 mic-goods 购物车列表接口返回的 CartItemVO 字段对齐；
 * 商品被删除时商品展示字段为 null，商品状态非 1（上架）时不可结算
 */
@Data
public class ShopCartItemVO implements Serializable {

    /** 商品ID（购物车条目以商品ID作为标识，结算时作为 goodsIds 传参） */
    private Long goodsId;

    /** 商品数量 */
    private Integer goodsNum;

    /** 商品名称（商品已删除时为null） */
    private String goodsName;

    /** 商品单价（商品已删除时为null） */
    private BigDecimal price;

    /** 当前库存（商品已删除时为null） */
    private Integer stock;

    /** 单次限购数量（商品已删除时为null） */
    private Integer limitBuyNum;

    /** 商品状态：0待审核 1上架（可购买） 2审核拒绝 3已下架（商品已删除时为null） */
    private Integer status;
}
