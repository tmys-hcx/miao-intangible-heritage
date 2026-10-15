package com.mic.goods.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单VO：订单主信息（含收货信息快照） + 商品明细列表（订单列表与详情共用）
 * 说明：明细来自下单时快照，商品后续被修改或删除不影响订单展示
 */
@Data
public class OrderItemVO implements Serializable {

    /** 订单ID（提交评价时回传，关联订单） */
    private Long id;

    /** 订单编号 */
    private String orderNo;

    /** 下单人账号（管理端订单列表由后端关联 sys_user 回填；用户端接口该字段为空） */
    private String userAccount;

    /** 订单总金额 */
    private BigDecimal totalPrice;

    /** 商品总件数（明细数量合计） */
    private Integer totalNum;

    /** 订单状态：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中 */
    private Integer orderStatus;

    /** 退款申请原因（买家填写，最近一次；订单处于 8退款申请中 时展示） */
    private String refundReason;

    /** 退款申请时间（最近一次，订单处于 8退款申请中 时展示） */
    private LocalDateTime refundApplyTime;

    /** 退款驳回原因（非遗人填写，最近一次；订单被驳回后展示，买家可重新申请） */
    private String refundRejectReason;

    /** 下单时间 */
    private LocalDateTime createTime;

    /** 收货人姓名（下单时地址快照） */
    private String receiverName;

    /** 收货人手机号（下单时地址快照） */
    private String receiverPhone;

    /** 收货完整地址：省市区+详细地址（下单时快照） */
    private String receiverAddress;

    /** 订单备注（选填） */
    private String orderRemark;

    /** 商品明细列表（下单时快照） */
    private List<OrderGoodsItemVO> items;
}
