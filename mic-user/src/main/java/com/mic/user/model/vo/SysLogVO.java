package com.mic.user.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统日志VO（管理端系统日志列表 / 详情）：
 * operate_user_id 关联 sys_user 回填操作人账号（userAccount），账号已删除时为 null
 */
@Data
public class SysLogVO {

    /** 日志ID */
    private Long id;

    /** 操作人ID */
    private Long operateUserId;

    /** 操作人账号（LEFT JOIN sys_user 回填，账号已删除时为 null） */
    private String userAccount;

    /** 操作模块（如：用户登录 / 用户注册 / 用户管理 / 申请审核） */
    private String operateModule;

    /** 操作内容（具体动作与对象描述） */
    private String operateContent;

    /** 客户端IP（网关代理场景取 X-Forwarded-For 首个地址） */
    private String ip;

    /** 操作时间 */
    private LocalDateTime createTime;
}
