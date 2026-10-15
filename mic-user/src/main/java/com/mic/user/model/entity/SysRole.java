package com.mic.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色表：统一角色体系（管理员细分角色 + 非遗传承人 + 普通用户）
 * 说明：user_role 与 sys_user.role 对齐（0管理员 1非遗人 2普通用户）；
 *      管理员经 sys_user.role_id 绑定细分角色，角色的功能分配决定管理端菜单与接口权限
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色名称（如：商城管理员） */
    private String roleName;

    /** 角色编码，唯一标识（SUPER_ADMIN / MALL_ADMIN / ...），编码决定系统兜底逻辑 */
    private String roleCode;

    /** 角色类别，与sys_user.role对应：0管理员 1非遗人 2普通用户 */
    private Integer userRole;

    /** 角色描述（职责说明，管理端展示） */
    private String roleDesc;

    /** 状态：1启用 0停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
