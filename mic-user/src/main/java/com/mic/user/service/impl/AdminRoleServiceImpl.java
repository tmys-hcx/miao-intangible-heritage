package com.mic.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mic.common.exception.BusinessException;
import com.mic.common.result.ResultCodeEnum;
import com.mic.user.mapper.SysAdminFunctionMapper;
import com.mic.user.mapper.SysRoleFunctionMapper;
import com.mic.user.mapper.SysRoleMapper;
import com.mic.user.mapper.UserMapper;
import com.mic.user.model.dto.AdminRoleSaveDTO;
import com.mic.user.model.entity.SysAdminFunction;
import com.mic.user.model.entity.SysRole;
import com.mic.user.model.entity.SysRoleFunction;
import com.mic.user.model.entity.SysUser;
import com.mic.user.service.AdminRoleService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 角色权限服务实现：管理端侧边栏功能查询 + 角色管理与功能权限分配
 * 说明：管理员（role=0）经 sys_user.role_id 绑定细分角色，角色的功能分配（sys_role_function）
 *      同时驱动管理端菜单展示（admin-functions 接口）与接口级鉴权（checkFunctionPermission）
 */
@Slf4j
@Service
public class AdminRoleServiceImpl implements AdminRoleService {

    /** 角色：0管理员（与 sys_user.role 对齐） */
    private static final int ROLE_ADMIN = 0;

    /** 状态：1启用 */
    private static final int STATUS_ENABLED = 1;

    /** 超级管理员角色编码：系统兜底角色，功能分配与删除均受保护 */
    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    @Resource
    private UserMapper userMapper;

    @Resource
    private SysRoleMapper sysRoleMapper;

    @Resource
    private SysAdminFunctionMapper sysAdminFunctionMapper;

    @Resource
    private SysRoleFunctionMapper sysRoleFunctionMapper;

    @Override
    public List<SysAdminFunction> getAdminFunctions(Long userId) {
        // 1.查当前登录用户：非管理员 / 未绑定角色时返回空列表（管理端菜单为空）
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(ROLE_ADMIN).equals(user.getRole()) || user.getRoleId() == null) {
            return List.of();
        }
        // 2.绑定角色须存在且启用（停用角色的功能不生效）
        SysRole role = sysRoleMapper.selectById(user.getRoleId());
        if (role == null || !Integer.valueOf(STATUS_ENABLED).equals(role.getStatus())) {
            return List.of();
        }
        // 3.查角色绑定的功能ID集合
        List<Long> functionIds = sysRoleFunctionMapper.selectList(new LambdaQueryWrapper<SysRoleFunction>()
                        .eq(SysRoleFunction::getRoleId, user.getRoleId()))
                .stream().map(SysRoleFunction::getFunctionId).toList();
        if (functionIds.isEmpty()) {
            return List.of();
        }
        // 4.按功能ID查启用中的功能，sort 升序（侧边栏展示顺序）
        return sysAdminFunctionMapper.selectList(new LambdaQueryWrapper<SysAdminFunction>()
                .in(SysAdminFunction::getId, functionIds)
                .eq(SysAdminFunction::getStatus, STATUS_ENABLED)
                .orderByAsc(SysAdminFunction::getSort)
                .orderByAsc(SysAdminFunction::getId));
    }

    @Override
    public void checkFunctionPermission(Long userId, String functionCode) {
        // 1.管理员身份校验（以库内数据为准，不信任请求头外的信息）
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(ROLE_ADMIN).equals(user.getRole()) || user.getRoleId() == null) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可操作");
        }
        // 2.绑定角色须存在且启用
        SysRole role = sysRoleMapper.selectById(user.getRoleId());
        if (role == null || !Integer.valueOf(STATUS_ENABLED).equals(role.getStatus())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "当前管理员角色已停用，请联系超级管理员");
        }
        // 3.功能点校验：当前角色必须拥有该功能权限（如超级管理员 / 系统管理员的 role:manage）
        SysAdminFunction function = sysAdminFunctionMapper.selectOne(new LambdaQueryWrapper<SysAdminFunction>()
                .eq(SysAdminFunction::getFunctionCode, functionCode)
                .eq(SysAdminFunction::getStatus, STATUS_ENABLED)
                .last("LIMIT 1"));
        if (function == null || sysRoleFunctionMapper.selectCount(new LambdaQueryWrapper<SysRoleFunction>()
                .eq(SysRoleFunction::getRoleId, user.getRoleId())
                .eq(SysRoleFunction::getFunctionId, function.getId())) == 0) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "当前角色无该功能权限，请联系超级管理员分配");
        }
    }

    @Override
    public List<SysRole> listRoles() {
        return sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getUserRole)
                .orderByAsc(SysRole::getId));
    }

    @Override
    public List<SysAdminFunction> listFunctions() {
        return sysAdminFunctionMapper.selectList(new LambdaQueryWrapper<SysAdminFunction>()
                .eq(SysAdminFunction::getStatus, STATUS_ENABLED)
                .orderByAsc(SysAdminFunction::getSort)
                .orderByAsc(SysAdminFunction::getId));
    }

    @Override
    public List<Long> getRoleFunctionIds(Long roleId) {
        return sysRoleFunctionMapper.selectList(new LambdaQueryWrapper<SysRoleFunction>()
                        .eq(SysRoleFunction::getRoleId, roleId))
                .stream().map(SysRoleFunction::getFunctionId).toList();
    }

    @Override
    public void assignRoleFunctions(Long roleId, List<Long> functionIds) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "角色不存在或已被删除");
        }
        if (SUPER_ADMIN_CODE.equals(role.getRoleCode())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "超级管理员默认拥有全部功能权限，不支持修改");
        }
        // 覆盖式保存：先物理清理旧关系，再按新集合重建（仅保留存在且启用的功能）
        sysRoleFunctionMapper.physicalDeleteByRoleId(roleId);
        List<SysAdminFunction> functions = functionIds == null || functionIds.isEmpty()
                ? List.of()
                : sysAdminFunctionMapper.selectList(new LambdaQueryWrapper<SysAdminFunction>()
                        .in(SysAdminFunction::getId, functionIds)
                        .eq(SysAdminFunction::getStatus, STATUS_ENABLED));
        for (SysAdminFunction function : functions) {
            SysRoleFunction relation = new SysRoleFunction();
            relation.setRoleId(roleId);
            relation.setFunctionId(function.getId());
            sysRoleFunctionMapper.insert(relation);
        }
        log.info("角色功能权限更新：roleId={}, roleCode={}, 功能数={}", roleId, role.getRoleCode(), functions.size());
    }

    @Override
    public void saveRole(AdminRoleSaveDTO dto) {
        // 1.角色编码必填 + 唯一性校验（数据库 uk_role_code 兜底）
        if (!StringUtils.hasText(dto.getRoleCode())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "角色编码不能为空");
        }
        if (sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, dto.getRoleCode().trim())) > 0) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "角色编码已存在，请更换");
        }
        // 2.角色类别合法性（与 sys_user.role 对齐：0管理员 1非遗人 2普通用户）
        if (dto.getUserRole() == null || dto.getUserRole() < 0 || dto.getUserRole() > 2) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "角色类别不合法（0管理员 1非遗人 2普通用户）");
        }
        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName().trim());
        role.setRoleCode(dto.getRoleCode().trim());
        role.setUserRole(dto.getUserRole());
        role.setRoleDesc(dto.getRoleDesc());
        role.setStatus(dto.getStatus() == null ? STATUS_ENABLED : dto.getStatus());
        sysRoleMapper.insert(role);
        log.info("角色新增：roleCode={}, roleName={}, userRole={}",
                role.getRoleCode(), role.getRoleName(), role.getUserRole());
    }

    @Override
    public void updateRole(AdminRoleSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "缺少角色ID");
        }
        SysRole role = sysRoleMapper.selectById(dto.getId());
        if (role == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "角色不存在或已被删除");
        }
        // 超级管理员为系统兜底角色，不允许停用（停用将导致全部超管菜单失效）
        if (SUPER_ADMIN_CODE.equals(role.getRoleCode()) && Integer.valueOf(0).equals(dto.getStatus())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "超级管理员为系统兜底角色，不允许停用");
        }
        // 仅更新名称 / 描述 / 状态：编码与类别为绑定关系依据，不允许修改
        SysRole update = new SysRole();
        update.setId(role.getId());
        update.setRoleName(dto.getRoleName().trim());
        update.setRoleDesc(dto.getRoleDesc());
        update.setStatus(dto.getStatus());
        sysRoleMapper.updateById(update);
        log.info("角色编辑：roleId={}, roleCode={}, roleName={}", role.getId(), role.getRoleCode(), update.getRoleName());
    }

    @Override
    public void removeRole(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "角色不存在或已被删除");
        }
        if (SUPER_ADMIN_CODE.equals(role.getRoleCode())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "超级管理员为系统兜底角色，不允许删除");
        }
        // 已被用户绑定的角色不可删除（避免用户 role_id 悬空、管理端菜单清空）
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleId, roleId)) > 0) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "该角色已被用户绑定，请先调整绑定用户后再删除");
        }
        // 逻辑删除角色，同时物理清理功能关系
        sysRoleMapper.deleteById(roleId);
        sysRoleFunctionMapper.physicalDeleteByRoleId(roleId);
        log.info("角色删除：roleId={}, roleCode={}", roleId, role.getRoleCode());
    }
}
