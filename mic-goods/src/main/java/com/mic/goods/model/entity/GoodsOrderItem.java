package com.mic.goods.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单商品明细实体（一单多商品）
 * 说明：下单时快照商品名称/图片/单价与卖家ID，商品后续被修改或删除不影响订单展示
 */
@Data
@TableName("goods_order_item")
public class GoodsOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID（关联goods_order.id） */
    private Long orderId;

    /** 订单编号（冗余，便于按单号查询） */
    private String orderNo;

    /** 商品ID */
    private Long goodsId;

    /** 卖家（非遗人）ID（下单时商品快照，用于卖家订单同步与查询） */
    private Long publisherId;

    /** 商品名称（下单时快照） */
    private String goodsName;

    /** 商品图片，JSON数组（下单时快照） */
    private String goodsImg;

    /** 商品单价（下单时快照） */
    private BigDecimal price;

    /** 购买数量 */
    private Integer goodsNum;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
