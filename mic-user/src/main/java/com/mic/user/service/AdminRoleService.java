package com.mic.user.service;

import com.mic.user.model.dto.AdminRoleSaveDTO;
import com.mic.user.model.entity.SysAdminFunction;
import com.mic.user.model.entity.SysRole;

import java.util.List;

/**
 * 角色权限服务：管理端侧边栏功能查询（按登录管理员角色）、角色管理与功能权限分配
 */
public interface AdminRoleService {

    /**
     * 当前登录管理员的功能列表（按绑定角色的功能分配过滤，sort 升序）
     * 管理端侧边栏据此动态渲染菜单；非管理员 / 未绑定角色 / 角色停用时返回空列表
     * @param userId 当前登录用户ID（网关透传）
     */
    List<SysAdminFunction> getAdminFunctions(Long userId);

    /**
     * 校验当前管理员是否拥有指定功能权限（角色管理接口的接口级鉴权，以库内数据为准）
     * @param userId 当前登录用户ID（网关透传）
     * @param functionCode 功能编码（如 role:manage）
     */
    void checkFunctionPermission(Long userId, String functionCode);

    /** 角色列表（角色管理页）：按类别（管理员在前）与ID升序 */
    List<SysRole> listRoles();

    /** 全部启用中的后台功能列表（权限分配弹窗）：sort 升序 */
    List<SysAdminFunction> listFunctions();

    /**
     * 角色已分配的功能ID集合（权限分配弹窗回显）
     * @param roleId 角色ID
     */
    List<Long> getRoleFunctionIds(Long roleId);

    /**
     * 保存角色功能分配（覆盖式：先清理旧关系再按新集合重建）
     * 超级管理员为系统兜底角色（默认全部功能），不允许修改
     * @param roleId 角色ID
     * @param functionIds 新功能ID集合（为空 = 取消全部功能）
     */
    void assignRoleFunctions(Long roleId, List<Long> functionIds);

    /** 新增角色：角色编码唯一、类别 0/1/2 校验 */
    void saveRole(AdminRoleSaveDTO dto);

    /** 编辑角色：仅名称 / 描述 / 状态可改（编码与类别为绑定关系依据，不可修改） */
    void updateRole(AdminRoleSaveDTO dto);

    /** 删除角色：超级管理员与已被用户绑定的角色不可删除，同时清理功能关系 */
    void removeRole(Long roleId);
}
