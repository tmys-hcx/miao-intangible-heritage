package com.mic.user.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.user.model.vo.SysLogVO;
import com.mic.user.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统日志接口（管理端）：
 * 日志分页查询（操作时间倒序），支持操作模块 / 操作人账号模糊搜索；
 * 日志数据由业务关键操作（登录 / 注册 / 用户管理 / 申请审核等）自动记录
 */
@Tag(name = "系统日志接口")
@RestController
@RequestMapping("/api/user/log")
@RequiredArgsConstructor
public class SysLogController {

    /** 角色：0管理员（系统日志查看操作） */
    private static final int ROLE_ADMIN = 0;

    private final SysLogService sysLogService;

    /**
     * 系统日志分页（管理员）：按操作时间倒序，支持操作模块 / 操作人账号模糊搜索
     */
    @Operation(summary = "系统日志分页（管理员）：默认每页10条，keyword模糊搜索操作模块/操作人账号")
    @GetMapping("/page")
    public Result<PageResult<SysLogVO>> page(PageDto pageDto,
                                             @RequestParam(value = "keyword", required = false) String keyword,
                                             @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        return Result.ok(sysLogService.pageLogs(pageDto, keyword));
    }

    /** 校验管理员角色：网关鉴权通过后透传 X-User-Role 可信头，非0（管理员）拒绝操作 */
    private void checkAdmin(Integer role) {
        if (role == null || role != ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可查看系统日志");
        }
    }
}
