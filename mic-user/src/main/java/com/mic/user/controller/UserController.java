package com.mic.user.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.vo.LoginUserVO;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.user.model.dto.UserInfoUpdateDTO;
import com.mic.user.model.dto.UserLoginDTO;
import com.mic.user.model.dto.UserPasswordUpdateDTO;
import com.mic.user.model.dto.UserRegisterDTO;
import com.mic.user.model.entity.SysUser;
import com.mic.user.model.vo.AdminStatsVO;
import com.mic.user.model.vo.CaptchaVO;
import com.mic.user.model.vo.UserLoginVO;
import com.mic.user.service.UserService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：验证码、注册、登录、用户信息、个人信息修改
 * + 管理端用户管理（分页列表 / 启用禁用 / 逻辑删除，仅管理员）
 */
@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    /** 角色：0管理员（用户管理操作） */
    private static final int ROLE_ADMIN = 0;

    private final UserService userService;

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(userService.createCaptcha());
    }

    @Operation(summary = "用户注册（仅普通用户）")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public Result<LoginUserVO> info(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 网关鉴权通过后透传 X-User-Id，缺失说明未登录
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
        return Result.ok(userService.getInfo(userId));
    }

    @Operation(summary = "用户分页列表（管理员）：可按角色 / 状态 / 关键词筛选，不含密码")
    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(PageDto pageDto,
                                            @RequestParam(value = "role", required = false) Integer role,
                                            @RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        return Result.ok(userService.pageAdminUsers(pageDto, role, status, keyword));
    }

    @Operation(summary = "修改个人信息（部分更新）：nickname / phone / identityInfo / avatar，null 字段不修改")
    @PutMapping("/info")
    public Result<LoginUserVO> updateInfo(@Valid @RequestBody UserInfoUpdateDTO dto,
                                          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
        return Result.ok(userService.updateUserInfo(userId, dto));
    }

    @Operation(summary = "修改登录密码：校验原密码，新密码长度6-20位且不能与原密码相同")
    @PutMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UserPasswordUpdateDTO dto,
                                       @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
        userService.updatePassword(userId, dto);
        return Result.ok();
    }

    @Operation(summary = "启用 / 禁用账号（管理员）：status=1启用、2禁用，管理员账号不可操作")
    @PutMapping("/status/{userId}")
    public Result<Void> changeStatus(@PathVariable("userId") Long userId,
                                     @RequestParam(value = "status") Integer status,
                                     @RequestHeader(value = "X-User-Id", required = false) Long adminId,
                                     @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        userService.changeUserStatus(userId, status, adminId);
        return Result.ok();
    }

    @Operation(summary = "逻辑删除用户（管理员）：管理员账号不可删除")
    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable("userId") Long userId,
                               @RequestHeader(value = "X-User-Id", required = false) Long adminId,
                               @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        userService.removeUser(userId, adminId);
        return Result.ok();
    }

    @Operation(summary = "首页数据统计（管理员）：用户总数 / 今日新增 / 角色分布 / 待审核非遗人申请数 / 近7天注册趋势")
    @GetMapping("/admin/stats")
    public Result<AdminStatsVO> adminStats(@RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        return Result.ok(userService.adminStats());
    }

    /** 校验管理员角色：网关鉴权通过后透传 X-User-Role 可信头，非0（管理员）拒绝操作 */
    private void checkAdmin(Integer role) {
        if (role == null || role != ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可管理用户");
        }
    }
}
