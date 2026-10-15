package com.mic.inheritReserve.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.vo.InheritSearchVO;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.inheritReserve.model.dto.InheritAuditDTO;
import com.mic.inheritReserve.model.dto.InheritSaveDTO;
import com.mic.inheritReserve.model.dto.VideoPublishDTO;
import com.mic.inheritReserve.model.entity.IntangibleVideo;
import com.mic.inheritReserve.model.vo.AdminStatsVO;
import com.mic.inheritReserve.model.vo.MyWatchVO;
import com.mic.inheritReserve.service.IntangibleInheritService;
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

import java.util.List;

/**
 * 非遗传承信息接口：
 * - 前台公开：分页（可按 infoType 筛选：1视频学习[仅已审核] 3访谈资料 4知识窗口）
 *   + 详情（type=1视频学习[仅已审核] 3访谈资料 4知识窗口；正文为 HTML 富文本，支持图文穿插排版，如公众号文章）
 * - 非遗人：发布学习视频（提交后进入待审核，管理员审核通过后前台展示）
 * - 个人中心：「我发布的视频」（仅非遗人，含全部审核状态，支持作者本人编辑 / 删除）/ 完整观看上报 +「我学习的视频」（登录用户均可）
 * - 管理员：后台分页（按 infoType 分发三张拆分局表，可按标题关键词 / 审核状态筛选） + 新增 / 编辑 / 删除
 *   + 学习视频审核（通过 / 驳回）；管理端维护与视频审核均已基于三张拆分局表
 * 登录态说明：网关鉴权通过后透传 X-User-Id / X-User-Role，服务据此识别当前用户与角色
 */
@Tag(name = "非遗传承信息接口")
@RestController
@RequestMapping("/api/inherit")
@RequiredArgsConstructor
public class IntangibleInheritController {

    /** 角色：0管理员（后台传承信息维护） */
    private static final int ROLE_ADMIN = 0;

    /** 角色：1非遗人（个人中心「我发布的视频」仅非遗人可查看） */
    private static final int ROLE_INHERITOR = 1;

    private final IntangibleInheritService intangibleInheritService;

    @Operation(summary = "公开传承信息分页（视频学习仅已审核；访谈资料 / 知识窗口为直发内容）")
    @GetMapping("/page")
    public Result<PageResult<?>> page(PageDto pageDto,
                                      @RequestParam(value = "infoType", required = false) Integer infoType) {
        return Result.ok(intangibleInheritService.pagePublic(pageDto, infoType));
    }

    @Operation(summary = "传承信息详情（公开）：type=1视频学习 3访谈资料 4知识窗口，缺省时依次在三个板块中查找")
    @GetMapping("/{id}")
    public Result<Object> detail(@PathVariable("id") Long id,
                                 @RequestParam(value = "type", required = false) Integer type) {
        return Result.ok(intangibleInheritService.getPublicDetail(id, type));
    }

    @Operation(summary = "资料检索（智能助手 Agent 专用）：按关键词聚合检索访谈资料 / 知识窗口 / 视频简介（仅已审核视频），返回纯文本片段")
    @GetMapping("/search")
    public Result<List<InheritSearchVO>> search(@RequestParam("keyword") String keyword,
                                                @RequestParam(value = "limit", required = false, defaultValue = "3") Integer limit) {
        return Result.ok(intangibleInheritService.searchMaterials(keyword, limit));
    }

    @Operation(summary = "后台传承信息分页（管理员）：按 infoType 分发三张拆分局表，可按标题关键词 / 审核状态筛选")
    @GetMapping("/admin/page")
    public Result<PageResult<?>> adminPage(
            PageDto pageDto,
            @RequestParam(value = "infoType", required = false) Integer infoType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
            @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        return Result.ok(intangibleInheritService.pageAdmin(pageDto, infoType, keyword, auditStatus));
    }

    @Operation(summary = "首页数据统计（管理员）：论坛帖子总量与待审核数 / 回复总量 / 学习视频总量与待审核数")
    @GetMapping("/admin/stats")
    public Result<AdminStatsVO> adminStats(@RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        return Result.ok(intangibleInheritService.adminStats());
    }

    @Operation(summary = "新增传承信息（管理员）：直接发布（audit_status=1），前台即刻可见")
    @PostMapping("/admin")
    public Result<Long> save(@Valid @RequestBody InheritSaveDTO dto,
                             @RequestHeader(value = "X-User-Id", required = false) Long userId,
                             @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        return Result.ok(intangibleInheritService.saveInherit(dto, userId));
    }

    @Operation(summary = "编辑传承信息（管理员）：按 infoType 定位所属拆分局表")
    @PutMapping("/admin/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody InheritSaveDTO dto,
                               @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        intangibleInheritService.updateInherit(id, dto);
        return Result.ok();
    }

    @Operation(summary = "删除传承信息（管理员，逻辑删除）：type=1视频学习 3访谈资料 4知识窗口，区分拆分局表同ID")
    @DeleteMapping("/admin/{id}")
    public Result<Void> delete(@PathVariable("id") Long id,
                               @RequestParam(value = "type", required = false) Integer type,
                               @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        intangibleInheritService.removeInherit(id, type);
        return Result.ok();
    }

    @Operation(summary = "发布学习视频（仅非遗人）：提交后进入待审核，管理员审核通过后前台展示")
    @PostMapping("/publish")
    public Result<Long> publishVideo(@Valid @RequestBody VideoPublishDTO dto,
                                     @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                     @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkLogin(userId);
        return Result.ok(intangibleInheritService.publishVideo(dto, userId, role));
    }

    @Operation(summary = "学习视频审核（管理员）：auditStatus=1审核通过、2审核拒绝（驳回，remark必填）")
    @PutMapping("/audit/{id}")
    public Result<Void> auditVideo(@PathVariable("id") Long id,
                                   @Valid @RequestBody InheritAuditDTO dto,
                                   @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                   @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        intangibleInheritService.auditVideo(id, dto.getAuditStatus(), dto.getRemark(), userId);
        return Result.ok();
    }

    @Operation(summary = "编辑我发布的学习视频（仅非遗人，作者本人）：编辑后重新进入待审核")
    @PutMapping("/video/{id}")
    public Result<Void> updateMyVideo(@PathVariable("id") Long id,
                                      @Valid @RequestBody VideoPublishDTO dto,
                                      @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                      @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkLogin(userId);
        checkInheritor(role);
        intangibleInheritService.updateMyVideo(id, dto, userId);
        return Result.ok();
    }

    @Operation(summary = "删除我发布的学习视频（仅非遗人，作者本人，逻辑删除）")
    @DeleteMapping("/video/{id}")
    public Result<Void> removeMyVideo(@PathVariable("id") Long id,
                                      @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                      @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkLogin(userId);
        checkInheritor(role);
        intangibleInheritService.removeMyVideo(id, userId);
        return Result.ok();
    }

    @Operation(summary = "我发布的视频分页（仅非遗人）：本人发布的全部学习视频，含审核状态与驳回原因")
    @GetMapping("/my/page")
    public Result<PageResult<IntangibleVideo>> myVideoPage(
            PageDto pageDto,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkLogin(userId);
        checkInheritor(role);
        return Result.ok(intangibleInheritService.pageMyPublish(pageDto, userId));
    }

    @Operation(summary = "上报完整观看（登录用户）：视频完整播放结束后调用，重复完整观看仅刷新最近观看时间")
    @PostMapping("/watch/{id}")
    public Result<Void> reportWatch(@PathVariable("id") Long id,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        intangibleInheritService.reportWatch(id, userId);
        return Result.ok();
    }

    @Operation(summary = "我学习的视频分页（登录用户）：仅已完整观看且视频已发布，按最近观看时间倒序")
    @GetMapping("/watch/my/page")
    public Result<PageResult<MyWatchVO>> myWatchPage(
            PageDto pageDto,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(intangibleInheritService.pageMyWatch(pageDto, userId));
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
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可管理传承信息");
        }
    }

    /** 校验非遗人角色：个人中心「我发布的视频」仅非遗人（本人）可查看 */
    private void checkInheritor(Integer role) {
        if (role == null || role != ROLE_INHERITOR) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅非遗人可查看发布的视频");
        }
    }
}
