package com.mic.goods.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 商品审核记录VO：审核完成后序列化为 JSON 存入 Redis（key：miao:goods:audit:record:{goodsId}，保留7天）
 * 供商品审核界面与非遗人「我发布的商品」回显审核结论、打回原因与审核时间
 */
@Data
public class GoodsAuditRecordVO implements Serializable {

    /** 商品ID */
    private Long goodsId;

    /** 商品名称（便于在 Redis 客户端中直接查看） */
    private String goodsName;

    /** 审核结论：1审核通过（上架） / 2审核拒绝（打回） */
    private Integer auditStatus;

    /** 审核备注：审核拒绝时为不合格原因（打回原因） */
    private String remark;

    /** 审核人（管理员）ID */
    private Long auditorId;

    /** 审核时间：yyyy-MM-dd HH:mm:ss */
    private String auditTime;
}
