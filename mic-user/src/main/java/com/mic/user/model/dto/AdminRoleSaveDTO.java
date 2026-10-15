package com.mic.user.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增 / 编辑入参
 * 新增：roleCode / userRole 必填；编辑（id 非空）：仅 roleName / roleDesc / status 生效，编码与类别不可改
 */
@Data
public class AdminRoleSaveDTO {

    /** 角色ID（编辑时必传） */
    private Long id;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称不能超过50个字符")
    private String roleName;

    /** 角色编码（新增时必填，唯一，如 MALL_ADMIN；编辑时忽略） */
    @Size(max = 50, message = "角色编码不能超过50个字符")
    private String roleCode;

    /** 角色类别（新增时必填：0管理员 1非遗人 2普通用户；编辑时忽略） */
    private Integer userRole;

    @Size(max = 200, message = "角色描述不能超过200个字符")
    private String roleDesc;

    /** 状态：1启用 0停用（默认启用） */
    private Integer status;
}
