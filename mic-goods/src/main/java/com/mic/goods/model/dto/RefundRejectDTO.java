package com.mic.goods.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 驳回退款申请入参DTO
 * 说明：非遗人（订单卖家）驳回买家的退款申请时须填写驳回原因，
 * 提交后订单恢复到申请前的状态（1待发货 / 2已发货），买家可修改原因后重新申请
 */
@Data
public class RefundRejectDTO implements Serializable {

    @NotBlank(message = "退款驳回原因不能为空")
    @Size(max = 512, message = "退款驳回原因长度不能超过512字符")
    private String rejectReason;
}
