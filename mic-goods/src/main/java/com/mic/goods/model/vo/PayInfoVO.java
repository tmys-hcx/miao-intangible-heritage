package com.mic.goods.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 沙箱模拟支付——手机端扫码确认页订单信息VO（免登录接口凭支付令牌返回）
 * 一次结算按商品拆分为多张订单共享结算批次号，本VO按批次返回：首张订单号 / 批次应付总额 / 订单笔数 / 展示状态
 */
@Data
public class PayInfoVO implements Serializable {

    /** 订单编号（结算批次内首张订单编号，仅作展示） */
    private String orderNo;

    /** 本次应付总金额（结算批次内全部订单金额合计，一次支付覆盖整批订单） */
    private BigDecimal totalPrice;

    /** 本次结算订单笔数（结算批次内订单数量，多笔时页面提示「共 N 笔订单一起支付」） */
    private Integer orderCount;

    /** 批次展示状态：0待付款（确认页可支付）；全部已取消→5已取消；支付后→1待发货（兜底展示状态） */
    private Integer orderStatus;
}
