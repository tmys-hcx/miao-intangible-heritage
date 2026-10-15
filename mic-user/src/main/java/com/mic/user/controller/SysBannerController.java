package com.mic.user.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.user.model.entity.SysBanner;
import com.mic.user.service.SysBannerService;
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
 * 轮播图接口：前台首页免登录分页展示 + 管理端增删改（仅管理员）
 * 媒体文件（图片/视频）经 mic-file 上传至阿里云OSS，本接口仅保存访问URL
 */
@Tag(name = "轮播图接口")
@RestController
@RequestMapping("/api/user/banner")
@RequiredArgsConstructor
public class SysBannerController {

    /** 角色：0管理员（轮播图管理操作） */
    private static final int ROLE_ADMIN = 0;

    private final SysBannerService sysBannerService;

    @Operation(summary = "轮播图分页列表（免登录）：按sort升序，keyword可选按描述搜索")
    @GetMapping("/page")
    public Result<PageResult<SysBanner>> page(PageDto pageDto,
                                              @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(sysBannerService.pageBanners(pageDto, keyword));
    }

    @Operation(summary = "新增轮播图（管理员）")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody SysBanner banner,
                             @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        sysBannerService.saveBanner(banner);
        return Result.ok();
    }

    @Operation(summary = "修改轮播图（管理员）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody SysBanner banner,
                               @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        banner.setId(id);
        sysBannerService.updateBanner(banner);
        return Result.ok();
    }

    @Operation(summary = "删除轮播图（管理员）：逻辑删除")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable("id") Long id,
                               @RequestHeader(value = "X-User-Role", required = false) Integer userRole) {
        checkAdmin(userRole);
        sysBannerService.removeBanner(id);
        return Result.ok();
    }

    /** 校验管理员角色：网关鉴权通过后透传 X-User-Role 可信头，非0（管理员）拒绝操作 */
    private void checkAdmin(Integer role) {
        if (role == null || role != ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可管理轮播图");
        }
    }
}
