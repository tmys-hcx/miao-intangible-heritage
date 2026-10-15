package com.mic.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色功能关系表：角色 × 后台功能（仅管理员类角色分配后台功能）
 */
@Data
@TableName("sys_role_function")
public class SysRoleFunction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID（sys_role.id） */
    private Long roleId;

    /** 后台功能ID（sys_admin_function.id） */
    private Long functionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
