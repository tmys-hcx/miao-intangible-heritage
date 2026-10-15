package com.mic.goods.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 收货地址保存入参DTO（新增 / 编辑共用）
 * 说明：用户ID由服务端从网关透传头获取；isDefault 为 1 时服务端自动清除本人其他默认地址
 */
@Data
public class AddressSaveDTO implements Serializable {

    /** 收货人姓名 */
    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 64, message = "收货人姓名长度不能超过64字符")
    private String receiverName;

    /** 收货人手机号（11位） */
    @NotBlank(message = "收货人手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String receiverPhone;

    /** 省份名称（省市区三级选择器） */
    @NotBlank(message = "省份不能为空")
    @Size(max = 32, message = "省份长度不能超过32字符")
    private String province;

    /** 城市名称（省市区三级选择器） */
    @NotBlank(message = "城市不能为空")
    @Size(max = 32, message = "城市长度不能超过32字符")
    private String city;

    /** 区/县名称（省市区三级选择器） */
    @NotBlank(message = "区/县不能为空")
    @Size(max = 32, message = "区/县长度不能超过32字符")
    private String district;

    /** 详细街道门牌号地址 */
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 256, message = "详细地址长度不能超过256字符")
    private String detailAddress;

    /** 是否设为默认地址：0否 1是（为空按0处理） */
    private Integer isDefault;
}
