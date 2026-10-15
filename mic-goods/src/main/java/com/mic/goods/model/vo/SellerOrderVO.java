package com.mic.goods.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 卖家订单VO（非遗人视角：本人商品被购买的订单）
 * 说明：下单时按卖家分组生成快照，存储于 Redis（miao:seller:order:{sellerId}，field=订单号），
 * 供非遗人「我的订单」界面查询；createTime 用字符串避免 JSON 序列化格式问题
 */
@Data
public class SellerOrderVO implements Serializable {

    /** 订单编号 */
    private String orderNo;

    /** 下单用户（买家）ID */
    private Long buyerId;

    /** 订单状态：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中 */
    private Integer orderStatus;

    /** 退款申请原因（买家填写，最近一次；订单处于 8退款申请中 时供非遗人审核处理） */
    private String refundReason;

    /** 退款申请时间：yyyy-MM-dd HH:mm:ss（最近一次，与退款申请原因同时写入） */
    private String refundApplyTime;

    /** 本单中该卖家商品小计金额 */
    private BigDecimal totalPrice;

    /** 本单中该卖家商品总件数 */
    private Integer totalNum;

    /** 下单时间：yyyy-MM-dd HH:mm:ss */
    private String createTime;

    /** 收货人姓名（下单时地址快照，供卖家发货） */
    private String receiverName;

    /** 收货人手机号（下单时地址快照，供卖家发货） */
    private String receiverPhone;

    /** 收货完整地址（省市区+详细地址，下单时快照，供卖家发货） */
    private String receiverAddress;

    /** 订单备注（选填） */
    private String orderRemark;

    /** 该卖家的商品明细（下单时快照） */
    private List<OrderGoodsItemVO> items;
}
