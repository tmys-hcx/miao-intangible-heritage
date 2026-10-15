package com.mic.agent.shop.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 申请退款入参DTO（Agent 侧契约类）：
 * 与 mic-goods 的 RefundApplyDTO 字段对齐；未收货前的已支付订单（1待发货 / 2已发货）可申请，
 * 提交后订单进入 8退款申请中，等待审核（同意退款 / 驳回后可修改原因重新申请）
 */
@Data
public class ShopRefundApplyDTO implements Serializable {

    /** 退款申请原因（必填，最长512字符） */
    private String refundReason;
}
