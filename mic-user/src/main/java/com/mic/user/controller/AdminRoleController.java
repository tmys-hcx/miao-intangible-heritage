package com.mic.user.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.user.model.dto.AdminRoleSaveDTO;
import com.mic.user.model.entity.SysAdminFunction;
import com.mic.user.model.entity.SysRole;
import com.mic.user.service.AdminRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色权限接口：管理端侧边栏功能查询（按登录管理员绑定角色）
 * + 角色管理（列表 / 新增 / 编辑 / 删除）与功能权限分配（均需 role:manage 功能权限）
 */
@Tag(name = "角色权限接口")
@RestController
@RequestMapping("/api/user/role")
@RequiredArgsConstructor
public class AdminRoleController {

    /** 角色管理功能编码：接口级权限校验（与 sys_admin_function.function_code 对齐） */
    private static final String FUNCTION_ROLE_MANAGE = "role:manage";

    private final AdminRoleService adminRoleService;

    @Operation(summary = "当前登录管理员的功能列表（管理端侧边栏按角色权限动态展示）")
    @GetMapping("/admin-functions")
    public Result<List<SysAdminFunction>> adminFunctions(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 网关鉴权通过后透传 X-User-Id，缺失说明未登录
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
        return Result.ok(adminRoleService.getAdminFunctions(userId));
    }

    @Operation(summary = "角色列表（管理员）：需角色管理权限")
    @GetMapping("/list")
    public Result<List<SysRole>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        adminRoleService.checkFunctionPermission(userId, FUNCTION_ROLE_MANAGE);
        return Result.ok(adminRoleService.listRoles());
    }

    @Operation(summary = "全部后台功能列表（管理员）：权限分配弹窗使用，需角色管理权限")
    @GetMapping("/function-list")
    public Result<List<SysAdminFunction>> functionList(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        adminRoleService.checkFunctionPermission(userId, FUNCTION_ROLE_MANAGE);
        return Result.ok(adminRoleService.listFunctions());
    }

    @Operation(summary = "角色已分配的功能ID集合（管理员）：分配弹窗回显，需角色管理权限")
    @GetMapping("/{roleId}/function-ids")
    public Result<List<Long>> roleFunctionIds(@PathVariable("roleId") Long roleId,
                                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        adminRoleService.checkFunctionPermission(userId, FUNCTION_ROLE_MANAGE);
        return Result.ok(adminRoleService.getRoleFunctionIds(roleId));
    }

    @Operation(summary = "保存角色功能分配（管理员）：覆盖式保存，需角色管理权限")
    @PutMapping("/{roleId}/function-ids")
    public Result<Void> assignFunctions(@PathVariable("roleId") Long roleId,
                                        @RequestBody(required = false) List<Long> functionIds,
                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        adminRoleService.checkFunctionPermission(userId, FUNCTION_ROLE_MANAGE);
        adminRoleService.assignRoleFunctions(roleId, functionIds);
        return Result.ok();
    }

    @Operation(summary = "新增角色（管理员）：需角色管理权限")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody AdminRoleSaveDTO dto,
                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        adminRoleService.checkFunctionPermission(userId, FUNCTION_ROLE_MANAGE);
        adminRoleService.saveRole(dto);
        return Result.ok();
    }

    @Operation(summary = "编辑角色（管理员）：仅名称 / 描述 / 状态，需角色管理权限")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody AdminRoleSaveDTO dto,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        adminRoleService.checkFunctionPermission(userId, FUNCTION_ROLE_MANAGE);
        adminRoleService.updateRole(dto);
        return Result.ok();
    }

    @Operation(summary = "删除角色（管理员）：超管与已被绑定的角色不可删，需角色管理权限")
    @DeleteMapping("/{roleId}")
    public Result<Void> remove(@PathVariable("roleId") Long roleId,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        adminRoleService.checkFunctionPermission(userId, FUNCTION_ROLE_MANAGE);
        adminRoleService.removeRole(roleId);
        return Result.ok();
    }
}
