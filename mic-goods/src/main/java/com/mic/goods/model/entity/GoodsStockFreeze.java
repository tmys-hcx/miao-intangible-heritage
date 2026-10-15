package com.mic.goods.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存冻结流水实体（TCC 分布式事务：下单 Redis 预扣库存）
 * 说明：Try 插入 T 记录（Redis 已预扣），Confirm 置 C（DB 落库扣减完成），Cancel 置 R（Redis 已还原）；
 * 唯一键 (order_no, goods_id) 保证二阶段重复投递时业务幂等；未终结（T）冻结参与启动预热时的可用库存回算
 */
@Data
@TableName("goods_stock_freeze")
public class GoodsStockFreeze {

    /** 冻结状态：已预留（Try 成功，Redis 已预扣） */
    public static final String STATUS_TRIED = "T";
    /** 冻结状态：已提交（Confirm 完成，DB 库存已落库扣减，待释放 Redis 冻结） */
    public static final String STATUS_COMMITTED = "C";
    /** 冻结状态：已释放（Confirm 全部完成：DB 已扣 + Redis 冻结已释放，终态） */
    public static final String STATUS_RELEASED = "F";
    /** 冻结状态：已取消（Cancel 完成，Redis 预扣已还原） */
    public static final String STATUS_ROLLED_BACK = "R";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Seata 全局事务ID（排查用：可与 tcc_fence_log / TC 控制台关联） */
    private String xid;

    /** 订单编号（库存预留凭据，下单时先行生成） */
    private String orderNo;

    /** 商品ID */
    private Long goodsId;

    /** 预留（冻结）数量 */
    private Integer num;

    /** 冻结状态：T已预留 C已提交 R已取消 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
