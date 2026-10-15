package com.mic.agent.shop.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单VO（Agent 侧契约类）：
 * 与 mic-goods 订单接口返回的 OrderItemVO 字段对齐（订单列表与详情共用）；
 * 订单状态：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中；
 * createTime 用 String 直收（服务端 JacksonConfig 统一按 yyyy-MM-dd HH:mm:ss 序列化，避免日期解析差异）
 */
@Data
public class ShopOrderVO implements Serializable {

    /** 订单ID */
    private Long id;

    /** 订单编号（用户报单号时用于 keyword 精确查找） */
    private String orderNo;

    /** 订单总金额 */
    private BigDecimal totalPrice;

    /** 商品总件数 */
    private Integer totalNum;

    /** 订单状态：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中 */
    private Integer orderStatus;

    /** 退款申请原因（最近一次；订单处于 8退款申请中 时展示） */
    private String refundReason;

    /** 退款驳回原因（最近一次；订单被驳回后展示，可重新申请） */
    private String refundRejectReason;

    /** 下单时间（yyyy-MM-dd HH:mm:ss） */
    private String createTime;

    /** 收货人姓名（下单时地址快照） */
    private String receiverName;

    /** 收货人手机号（下单时地址快照） */
    private String receiverPhone;

    /** 收货完整地址（下单时地址快照） */
    private String receiverAddress;

    /** 订单备注 */
    private String orderRemark;

    /** 商品明细列表（下单时快照） */
    private List<ShopOrderGoodsItemVO> items;
}
