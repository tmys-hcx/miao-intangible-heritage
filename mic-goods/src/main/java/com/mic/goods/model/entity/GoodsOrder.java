package com.mic.goods.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体（一次结算按商品拆分为多张订单：每张订单含单件商品，商品明细见 goods_order_item；
 * 同批次订单共享 batch_no，支付时整批一起支付）
 * 说明：下单时复制收货地址为订单快照（地址后续被修改/删除不影响已有订单）
 */
@Data
@TableName("goods_order")
public class GoodsOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "订单编号不能为空")
    @Size(max = 64, message = "订单编号长度不能超过64字符")
    private String orderNo;

    /** 结算批次号（一次购物车结算按商品拆分的多张订单共享；支付会话按批次组织，整批一起支付；历史单商品订单为空） */
    @Size(max = 64, message = "结算批次号长度不能超过64字符")
    private String batchNo;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "收货地址ID不能为空")
    private Long addressId;

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 64, message = "收货人姓名长度不能超过64字符")
    private String receiverName;

    @NotBlank(message = "收货人手机号不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货完整地址不能为空")
    @Size(max = 512, message = "收货地址长度不能超过512字符")
    private String receiverAddress;

    /** 订单备注（选填） */
    @Size(max = 512, message = "订单备注长度不能超过512字符")
    private String orderRemark;

    @NotNull(message = "订单总金额不能为空")
    @PositiveOrZero(message = "订单金额不能为负数")
    private BigDecimal totalPrice;

    /** 订单状态：0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中（待非遗人审核）（流转规则见 GoodsOrderService 类注释） */
    @NotNull(message = "订单状态不能为空")
    private Integer orderStatus;

    /** 退款申请原因（买家填写，最近一次；非遗人驳回后可修改重新申请） */
    @Size(max = 512, message = "退款申请原因长度不能超过512字符")
    private String refundReason;

    /** 退款申请时间（最近一次） */
    private LocalDateTime refundApplyTime;

    /** 退款申请时的订单状态（1待发货 / 2已发货，非遗人驳回时恢复该状态） */
    private Integer refundPrevStatus;

    /** 退款驳回原因（非遗人填写，最近一次；买家可据此修改后重新申请） */
    @Size(max = 512, message = "退款驳回原因长度不能超过512字符")
    private String refundRejectReason;

    /** 下单人账号（非表字段）：管理端订单列表自定义 SQL 关联 sys_user 回填，展示下单人替代ID */
    @TableField(exist = false)
    private String userAccount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
