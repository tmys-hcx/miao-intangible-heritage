package com.mic.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 智能助手对话响应VO（/api/agent/chat）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatReplyVO implements Serializable {

    /** AI 回复文本 */
    private String reply;

    /**
     * 本次对话创建的待付款订单编号（商品主题下单流使用）：
     * 非空表示购物工具已创建订单，前端据此弹出扫码支付引导并跳转沙箱模拟支付页（/pay/订单号）
     */
    private String payOrderNo;
}
