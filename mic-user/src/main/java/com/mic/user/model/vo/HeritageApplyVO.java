package com.mic.user.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 非遗人申请VO：申请内容 + 申请人基础信息
 * 用于用户端查询申请状态、管理端申请审核分页 / 详情回显
 */
@Data
public class HeritageApplyVO implements Serializable {

    /** 申请ID */
    private Long id;

    /** 申请用户ID */
    private Long userId;

    /** 账号（申请人基础信息） */
    private String account;

    /** 昵称（申请人基础信息） */
    private String nickname;

    /** 头像（申请人基础信息） */
    private String avatar;

    /** 手机号（申请人基础信息） */
    private String phone;

    /** 身份信息（传承经历、师承关系、技艺掌握情况等） */
    private String identityInfo;

    /** 申请原因 */
    private String applyReason;

    /** 证明材料图片地址，JSON数组字符串（前端解析为图片列表展示） */
    private String materialUrls;

    /** 审核状态：0待审核，1已通过，2已驳回 */
    private Integer auditStatus;

    /** 审核备注/驳回原因 */
    private String auditRemark;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 申请时间 */
    private LocalDateTime createTime;
}
