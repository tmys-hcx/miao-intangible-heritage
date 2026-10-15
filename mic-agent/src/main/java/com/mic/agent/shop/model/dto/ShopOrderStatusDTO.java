package com.mic.agent.shop.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新订单状态入参DTO（Agent 侧契约类）：
 * 与 mic-goods 的 OrderStatusDTO 字段对齐；购物助手仅用于取消订单（orderStatus=5，0待付款→5已取消并归还库存），
 * 付款 1 / 发货 2 / 收货 3 由用户或卖家在对应页面操作，不开放给助手
 */
@Data
public class ShopOrderStatusDTO implements Serializable {

    /** 目标状态：5取消（0待付款→5已取消，归还库存） */
    private Integer orderStatus;
}
