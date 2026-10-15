package com.mic.culture.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.culture.model.dto.CultureAuditDTO;
import com.mic.culture.model.dto.CultureSaveDTO;
import com.mic.culture.model.dto.CultureSortDTO;
import com.mic.culture.model.entity.IntangibleCulture;
import com.mic.culture.model.vo.AdminStatsVO;
import com.mic.culture.service.IntangibleCultureService;
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
 * 非遗文化资料接口：
 * - 前台公开：分页（仅已发布）+ 详情（浏览量自增，免登录；未发布资料仅发布者本人可预览）
 * - 非遗人：我的发布分页 + 发布 / 修改 / 删除（仅操作本人发布资料）
 * - 管理员：资料审核（审核通过发布 / 审核驳回） + 后台资料分页（含待审核 / 已驳回） + 前台排版管理（拖拽保存展示顺序）
 * 登录态说明：网关鉴权通过后透传 X-User-Id / X-User-Role，服务据此识别当前用户与角色
 */
@Tag(name = "非遗文化资料接口")
@RestController
@RequestMapping("/api/culture")
@RequiredArgsConstructor
public class IntangibleCultureController {

    /** 角色：0管理员（资料审核 / 后台分页） */
    private static final int ROLE_ADMIN = 0;

    private final IntangibleCultureService intangibleCultureService;

    @Operation(summary = "非遗资料公开分页（前台展示已发布资料，支持关键词 / 最新 / 最热 / 排版推荐排序）")
    @GetMapping("/page")
    public Result<PageResult<IntangibleCulture>> page(PageDto pageDto,
                                                      @RequestParam(value = "keyword", required = false) String keyword,
                                                      @RequestParam(value = "sortBy", required = false) String sortBy) {
        return Result.ok(intangibleCultureService.pagePublished(pageDto, keyword, sortBy));
    }

    @Operation(summary = "非遗资料详情（免登录；已发布浏览量自增 +1，未发布资料仅发布者本人可预览）")
    @GetMapping("/{id}")
    public Result<IntangibleCulture> detail(@PathVariable("id") Long id,
                                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.ok(intangibleCultureService.getDetail(id, userId));
    }

    @Operation(summary = "我的发布分页（仅非遗人，含待审核 / 已驳回）")
    @GetMapping("/my/page")
    public Result<PageResult<IntangibleCulture>> myPage(PageDto pageDto,
                                                        @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                        @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkLogin(userId);
        return Result.ok(intangibleCultureService.pageMy(userId, role, pageDto));
    }

    @Operation(summary = "发布非遗资料（仅非遗人）：提交后进入待审核，管理员审核通过后在前台展示")
    @PostMapping
    public Result<Long> publish(@Valid @RequestBody CultureSaveDTO dto,
                                @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkLogin(userId);
        return Result.ok(intangibleCultureService.publish(userId, role, dto));
    }

    @Operation(summary = "修改非遗资料（仅非遗人，仅本人发布）：修改后重新进入待审核")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody CultureSaveDTO dto,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId,
                               @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkLogin(userId);
        intangibleCultureService.updateCulture(id, userId, role, dto);
        return Result.ok();
    }

    @Operation(summary = "删除非遗资料（仅非遗人，逻辑删除本人发布）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId,
                               @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkLogin(userId);
        intangibleCultureService.removeCulture(id, userId, role);
        return Result.ok();
    }

    @Operation(summary = "非遗资料审核（管理员）：auditStatus=1审核通过（发布）、2审核拒绝（驳回，remark必填）")
    @PutMapping("/audit/{id}")
    public Result<Void> audit(@PathVariable("id") Long id,
                              @Valid @RequestBody CultureAuditDTO dto,
                              @RequestHeader(value = "X-User-Id", required = false) Long userId,
                              @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        intangibleCultureService.auditCulture(id, dto.getAuditStatus(), dto.getRemark(), userId);
        return Result.ok();
    }

    @Operation(summary = "后台资料分页（管理员）：默认每页10条，可传auditStatus筛选（0待审核 1已发布 2已驳回）")
    @GetMapping("/admin/page")
    public Result<PageResult<IntangibleCulture>> adminPage(PageDto pageDto,
                                                           @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
                                                           @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        return Result.ok(intangibleCultureService.pageAdminCulture(pageDto, auditStatus));
    }

    @Operation(summary = "保存非遗文化排版（管理员）：提交拖拽后的文章ID有序列表，数组顺序即前台默认展示顺序")
    @PutMapping("/sort")
    public Result<Void> saveSortOrder(@Valid @RequestBody CultureSortDTO dto,
                                      @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        intangibleCultureService.saveSortOrder(dto.getIds());
        return Result.ok();
    }

    @Operation(summary = "首页数据统计（管理员）：资料总数 / 待审核·已发布·已驳回数 / 总浏览量 / 近7天发布趋势")
    @GetMapping("/admin/stats")
    public Result<AdminStatsVO> adminStats(@RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        return Result.ok(intangibleCultureService.adminStats());
    }

    /** 网关鉴权通过后透传 X-User-Id，缺失说明未登录 */
    private void checkLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
    }

    /** 校验管理员角色：网关鉴权通过后透传 X-User-Role 可信头，非0（管理员）拒绝操作 */
    private void checkAdmin(Integer role) {
        if (role == null || role != ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可审核或管理非遗资料");
        }
    }
}
