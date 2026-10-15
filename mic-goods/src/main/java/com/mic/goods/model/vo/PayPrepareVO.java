package com.mic.goods.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 沙箱模拟支付——发起支付返回VO（电脑端模拟支付页生成二维码使用）
 * 一次结算按商品拆分为多张订单共享结算批次号，本VO返回批次应付总额供支付页展示（一次支付覆盖整批订单）
 */
@Data
public class PayPrepareVO implements Serializable {

    /** 一次性支付令牌（手机端确认页链接携带：/pay/confirm?token=xxx，凭令牌完成批次内全部待付款订单的支付） */
    private String payToken;

    /** 服务端局域网 IPv4 地址（二维码需手机可访问，不能使用 localhost） */
    private String lanIp;

    /** 支付剩余秒数（批次内最早下单时间起算，与 Redis 15 分钟支付超时标记一致，用于页面倒计时） */
    private Integer expireSeconds;

    /** 本次应付总金额（结算批次内全部待付款订单金额合计，一次结算多商品拆单后一起支付） */
    private BigDecimal totalPrice;
}
