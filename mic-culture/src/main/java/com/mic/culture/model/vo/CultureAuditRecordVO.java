package com.mic.culture.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 资料审核记录VO：审核完成后序列化为 JSON 存入 Redis（key：miao:culture:audit:record:{cultureId}，保留7天）
 * 供资料审核界面与非遗人「我发布的资料」回显审核结论、驳回原因与审核时间
 */
@Data
public class CultureAuditRecordVO implements Serializable {

    /** 资料ID */
    private Long cultureId;

    /** 资料标题（便于在 Redis 客户端中直接查看） */
    private String cultureTitle;

    /** 审核结论：1审核通过（发布） / 2审核拒绝（驳回） */
    private Integer auditStatus;

    /** 审核备注：审核拒绝时为不合格原因（驳回原因） */
    private String remark;

    /** 审核人（管理员）ID */
    private Long auditorId;

    /** 审核时间：yyyy-MM-dd HH:mm:ss */
    private String auditTime;
}
