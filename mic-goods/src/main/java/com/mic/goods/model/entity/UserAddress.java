package com.mic.goods.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户收货地址实体
 * 说明：收货地址归属交易域（与订单/购物车同模块），下单时复制为订单收货信息快照
 * 约束：同一用户默认地址唯一（is_default=1 至多一条）
 */
@Data
@TableName("user_address")
public class UserAddress {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 64, message = "收货人姓名长度不能超过64字符")
    private String receiverName;

    @NotBlank(message = "收货人手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String receiverPhone;

    @NotBlank(message = "省份不能为空")
    @Size(max = 32, message = "省份长度不能超过32字符")
    private String province;

    @NotBlank(message = "城市不能为空")
    @Size(max = 32, message = "城市长度不能超过32字符")
    private String city;

    @NotBlank(message = "区/县不能为空")
    @Size(max = 32, message = "区/县长度不能超过32字符")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 256, message = "详细地址长度不能超过256字符")
    private String detailAddress;

    /** 是否默认地址：0否 1是（同一用户至多一条默认地址） */
    @NotNull(message = "是否默认地址不能为空")
    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
