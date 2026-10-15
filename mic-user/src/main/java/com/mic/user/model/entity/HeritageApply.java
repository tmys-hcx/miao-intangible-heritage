package com.mic.user.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 非遗人申请表（heritage_apply）
 * 流程：普通用户在个人中心提交申请（含身份信息、申请原因、证明材料）→ 管理员审核
 * 审核通过：sys_user.role 置为1（非遗人）；审核驳回：记录驳回原因，可重新申请
 */
@Data
@TableName("heritage_apply")
public class HeritageApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请用户ID，关联sys_user.id */
    private Long userId;

    /** 身份信息（传承经历、师承关系、技艺掌握情况等） */
    private String identityInfo;

    /** 申请原因 */
    private String applyReason;

    /** 证明材料图片地址，JSON数组字符串 */
    private String materialUrls;

    /** 审核状态：0待审核，1已通过，2已驳回 */
    private Integer auditStatus;

    /** 审核备注/驳回原因 */
    private String auditRemark;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核管理员ID，关联sys_user.id */
    private Long auditorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
