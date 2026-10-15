package com.mic.agent.shop.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 加入购物车入参DTO（Agent 侧契约类）：
 * 与 mic-goods 购物车加购接口的 CartAddDTO 字段对齐；用户身份由 Feign 拦截器透传 X-User-Id 头确定
 */
@Data
public class ShopCartAddDTO implements Serializable {

    /** 商品ID */
    private Long goodsId;

    /** 商品数量（大于0；同一商品重复加入时服务端数量累加） */
    private Integer goodsNum;
}
