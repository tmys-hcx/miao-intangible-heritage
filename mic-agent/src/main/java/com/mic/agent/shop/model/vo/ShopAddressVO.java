package com.mic.agent.shop.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 收货地址VO（Agent 侧契约类）：
 * 与 mic-goods 收货地址接口返回的 UserAddress 字段对齐（地址归属交易域，与购物车 / 订单同模块）
 */
@Data
public class ShopAddressVO implements Serializable {

    /** 地址ID（创建订单时作为 addressId 传参） */
    private Long id;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号 */
    private String receiverPhone;

    /** 省份名称 */
    private String province;

    /** 城市名称 */
    private String city;

    /** 区/县名称 */
    private String district;

    /** 详细街道门牌号地址 */
    private String detailAddress;

    /** 是否默认地址：0否 1是 */
    private Integer isDefault;
}
