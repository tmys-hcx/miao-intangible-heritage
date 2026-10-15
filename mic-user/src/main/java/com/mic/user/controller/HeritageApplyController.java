package com.mic.user.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.user.model.dto.HeritageApplyDTO;
import com.mic.user.model.dto.HeritageAuditDTO;
import com.mic.user.model.vo.HeritageApplyVO;
import com.mic.user.service.HeritageApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 非遗人申请接口
 * 用户端：提交申请 / 查询我的最新申请（要求个人信息完善，见服务层校验）
 * 管理端：申请分页列表 / 申请审核（仅管理员，通过后申请人转为非遗人）
 */
@Tag(name = "非遗人申请接口")
@RestController
@RequestMapping("/api/user/heritage-apply")
@RequiredArgsConstructor
public class HeritageApplyController {

    /** 角色：0管理员（申请审核操作） */
    private static final int ROLE_ADMIN = 0;

    private final HeritageApplyService heritageApplyService;

    @Operation(summary = "提交非遗人申请（普通用户）：要求个人信息完善、无审核中的申请")
    @PostMapping
    public Result<Void> submit(@Valid @RequestBody HeritageApplyDTO dto,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
        heritageApplyService.submitApply(userId, dto);
        return Result.ok();
    }

    @Operation(summary = "我的最新申请（用户端）：无申请记录时 data 为 null")
    @GetMapping("/mine")
    public Result<HeritageApplyVO> mine(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
        return Result.ok(heritageApplyService.getMyLatestApply(userId));
    }

    @Operation(summary = "申请分页列表（管理员）：可按审核状态筛选（0待审核 1已通过 2已驳回）")
    @GetMapping("/page")
    public Result<PageResult<HeritageApplyVO>> page(PageDto pageDto,
                                                    @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
                                                    @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        return Result.ok(heritageApplyService.pageApplies(pageDto, auditStatus));
    }

    @Operation(summary = "申请审核（管理员）：auditStatus=1通过（申请人转为非遗人）、2驳回（remark必填）")
    @PutMapping("/audit/{applyId}")
    public Result<Void> audit(@PathVariable("applyId") Long applyId,
                              @Valid @RequestBody HeritageAuditDTO dto,
                              @RequestHeader(value = "X-User-Id", required = false) Long adminId,
                              @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        heritageApplyService.auditApply(applyId, dto, adminId);
        return Result.ok();
    }

    /** 校验管理员角色：网关鉴权通过后透传 X-User-Role 可信头，非0（管理员）拒绝操作 */
    private void checkAdmin(Integer role) {
        if (role == null || role != ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可审核申请");
        }
    }
}
