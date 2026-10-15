package com.mic.agent.shop.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建订单入参DTO（Agent 侧契约类）：
 * 与 mic-goods 订单创建接口的 OrderCreateDTO 字段对齐（购物车结算：选中商品ID列表 + 收货地址ID）；
 * 订单金额由服务端按商品价格计算，不信任前端/模型传值；
 * 一次结算按商品拆分为多张独立订单（共享结算批次号，一次扫码支付整批订单）
 */
@Data
public class ShopOrderCreateDTO implements Serializable {

    /** 选中结算的购物车商品ID列表（购物车以商品ID作为条目标识） */
    private List<Long> goodsIds;

    /** 选中收货地址ID */
    private Long addressId;

    /** 订单备注（选填） */
    private String orderRemark;

    /** 订单初始状态：购物助手固定传 0待付款（创建后跳转沙箱模拟支付页扫码支付；15分钟未支付自动取消并归还库存） */
    private Integer orderStatus;
}
