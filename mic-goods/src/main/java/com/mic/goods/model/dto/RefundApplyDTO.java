package com.mic.goods.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 申请退款入参DTO
 * 说明：未收货前的订单（1待发货 / 2已发货）可申请；买家须填写退款原因，
 * 提交后订单进入 8退款申请中，等待非遗人审核（同意退款 / 驳回后可修改原因重新申请）
 */
@Data
public class RefundApplyDTO implements Serializable {

    @NotBlank(message = "退款申请原因不能为空")
    @Size(max = 512, message = "退款申请原因长度不能超过512字符")
    private String refundReason;
}
