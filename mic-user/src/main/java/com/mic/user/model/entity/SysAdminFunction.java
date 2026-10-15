package com.mic.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台管理功能表：管理端菜单功能登记（一行一个后台功能）
 * 说明：管理端侧边栏按「登录管理员绑定角色」的功能分配动态展示；
 *       function_code 作为接口级权限标识（如 role:manage）
 */
@Data
@TableName("sys_admin_function")
public class SysAdminFunction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 功能名称（管理端菜单名） */
    private String functionName;

    /** 功能编码，唯一权限标识（如 goods:audit） */
    private String functionCode;

    /** 前端路由路径（管理端菜单跳转地址，如 /admin/goods-audit） */
    private String routePath;

    /** 排序（管理端菜单展示顺序，数字越小越靠前） */
    private Integer sort;

    /** 状态：1启用 0停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
