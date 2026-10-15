package com.mic.agent.shop.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 新增收货地址入参DTO（Agent 侧契约类）：
 * 与 mic-goods 收货地址新增接口的 AddressSaveDTO 字段对齐，按后端校验规则：
 * 手机号 11 位、省 / 市 / 区 / 详细地址必填；用户身份由 Feign 拦截器透传 X-User-Id 头确定
 */
@Data
public class ShopAddressSaveDTO implements Serializable {

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号（11位，1开头） */
    private String receiverPhone;

    /** 省份名称 */
    private String province;

    /** 城市名称 */
    private String city;

    /** 区/县名称 */
    private String district;

    /** 详细街道门牌号地址 */
    private String detailAddress;

    /** 是否设为默认地址：0否 1是（智能助手新增默认设为1，便于后续结算直接使用） */
    private Integer isDefault;
}
