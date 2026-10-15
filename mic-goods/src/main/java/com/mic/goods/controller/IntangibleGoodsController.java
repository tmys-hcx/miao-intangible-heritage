package com.mic.goods.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.model.dto.GoodsAuditDTO;
import com.mic.goods.model.dto.GoodsSaveDTO;
import com.mic.goods.model.entity.IntangibleGoods;
import com.mic.goods.model.vo.AdminStatsVO;
import com.mic.goods.service.IntangibleGoodsService;
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
 * 非遗商品接口：前台商品分页、商品详情 + 非遗人商品发布 / 修改 / 删除 / 下架 + 管理员商品审核 / 后台分页
 * 发布、修改、删除、下架需登录且角色为非遗人；审核、后台分页需登录且角色为管理员
 * （网关鉴权后透传 X-User-Id / X-User-Role 可信头）
 */
@Tag(name = "非遗商品接口")
@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class IntangibleGoodsController {

    /** 角色：0管理员（商品审核 / 后台分页） */
    private static final int ROLE_ADMIN = 0;

    /** 角色：1非遗人（发布 / 管理本人商品） */
    private static final int ROLE_INHERITOR = 1;

    /** 前台「商品推荐」板块展示条数：固定最新上架 9 个（3×3 宫格，不分页） */
    private static final int LATEST_GOODS_LIMIT = 9;

    private final IntangibleGoodsService intangibleGoodsService;

    /**
     * 前台商品分页：查询已审核商品列表，支持分类筛选 / 关键词搜索 / 排序
     */
    @Operation(summary = "前台商品分页：仅返回已审核商品，可传categoryId筛选、keyword搜索名称/描述、sort排序（hot最热，默认最新）")
    @GetMapping("/page")
    public Result<PageResult<IntangibleGoods>> page(PageDto pageDto,
                                                    @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                    @RequestParam(value = "keyword", required = false) String keyword,
                                                    @RequestParam(value = "sort", required = false) String sort) {
        return Result.ok(intangibleGoodsService.pageGoods(pageDto, categoryId, keyword, sort));
    }

    /**
     * 前台最新上架商品推荐：按上架时间倒序返回最新 9 个已审核商品（3×3 宫格展示，不分页）
     * 支持分类筛选 / 关键词搜索，筛选后同样只取匹配结果中最新的 9 个
     */
    @Operation(summary = "前台最新上架商品：不分页，返回最新9个已审核商品，可传categoryId筛选、keyword搜索")
    @GetMapping("/latest")
    public Result<List<IntangibleGoods>> latest(@RequestParam(value = "categoryId", required = false) Long categoryId,
                                                @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(intangibleGoodsService.listLatestGoods(categoryId, keyword, LATEST_GOODS_LIMIT));
    }

    /**
     * 商品详情：仅返回已审核商品
     * 路径限定为纯数字ID（与网关免登录白名单正则 ^/api/goods/\d+$ 一致），
     * 非数字路径（如 /api/goods/publish）不再匹配本接口，统一按404处理
     */
    @Operation(summary = "商品详情：仅返回已审核商品")
    @GetMapping("/{id:\\d+}")
    public Result<IntangibleGoods> detail(@PathVariable("id") Long id) {
        return Result.ok(intangibleGoodsService.getGoodsDetail(id));
    }

    /**
     * 发布非遗商品（非遗人）：上架后进入待审核
     */
    @Operation(summary = "发布非遗商品（非遗人）：初始状态待审核，返回新增商品ID")
    @PostMapping
    public Result<Long> publish(@Valid @RequestBody GoodsSaveDTO dto,
                                @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkInheritor(userId, role, "发布");
        return Result.ok(intangibleGoodsService.publishGoods(dto, userId));
    }

    /**
     * 修改商品（非遗人）：仅可修改本人发布的商品，修改后重新进入待审核
     */
    @Operation(summary = "修改商品（非遗人）：仅可修改本人发布的商品，修改后重新进入待审核")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody GoodsSaveDTO dto,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId,
                               @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkInheritor(userId, role, "修改");
        intangibleGoodsService.updateGoods(id, dto, userId);
        return Result.ok();
    }

    /**
     * 删除商品（非遗人）：仅可删除本人发布的商品（逻辑删除）
     */
    @Operation(summary = "删除商品（非遗人）：仅可删除本人发布的商品（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId,
                               @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkInheritor(userId, role, "删除");
        intangibleGoodsService.deleteGoods(id, userId);
        return Result.ok();
    }

    /**
     * 下架商品（非遗人）：仅可下架本人发布的已上架商品
     * 下架后（3已下架）前台不可见、不可搜索、不可加购 / 下单；库存售罄的已上架商品由下单扣减库存流程自动下架
     */
    @Operation(summary = "下架商品（非遗人）：仅可下架本人已上架的商品，下架后前台不可见")
    @PutMapping("/{id}/off-shelf")
    public Result<Void> offShelf(@PathVariable("id") Long id,
                                 @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                 @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkInheritor(userId, role, "下架");
        intangibleGoodsService.offShelfGoods(id, userId);
        return Result.ok();
    }

    /**
     * 商品审核操作（管理员）：审核通过上架（前台可见），审核拒绝打回（前台不可见，需填写不合格原因）
     * 审核结论与备注存入 Redis，供审核界面与非遗人查看
     */
    @Operation(summary = "商品审核操作（管理员）：auditStatus=1审核通过（上架）、2审核拒绝（打回，remark必填）")
    @PutMapping("/audit/{id}")
    public Result<Void> audit(@PathVariable("id") Long id,
                              @Valid @RequestBody GoodsAuditDTO dto,
                              @RequestHeader(value = "X-User-Id", required = false) Long userId,
                              @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        intangibleGoodsService.auditGoods(id, dto.getAuditStatus(), dto.getRemark(), userId);
        return Result.ok();
    }

    /**
     * 后台商品分页（管理员）：查询全部商品（含待审核 / 已拒绝 / 已下架），可按状态筛选
     */
    @Operation(summary = "后台商品分页（管理员）：默认每页10条，可传auditStatus筛选（0待审核 1通过 2打回 3已下架）")
    @GetMapping("/admin/page")
    public Result<PageResult<IntangibleGoods>> adminPage(PageDto pageDto,
                                                         @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
                                                         @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        return Result.ok(intangibleGoodsService.pageAdminGoods(pageDto, auditStatus));
    }

    @Operation(summary = "首页数据统计（管理员）：商品总数与状态分布（待审核·上架·打回·下架）/ 订单总数与状态分布 / 成交金额 / 近7天订单趋势")
    @GetMapping("/admin/stats")
    public Result<AdminStatsVO> adminStats(@RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        return Result.ok(intangibleGoodsService.adminStats());
    }

    /**
     * 我发布的商品分页（非遗人）：含待审核 / 已上架 / 已打回 / 已下架，可按状态筛选
     * 已打回商品的打回原因从 Redis 审核记录回填，供修改后重新提交
     */
    @Operation(summary = "我发布的商品（非遗人）：默认每页10条，可传status筛选（0待审核 1上架 2打回 3已下架），含打回原因")
    @GetMapping("/mine")
    public Result<PageResult<IntangibleGoods>> mine(PageDto pageDto,
                                                    @RequestParam(value = "status", required = false) Integer status,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                    @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkInheritor(userId, role, "查看");
        return Result.ok(intangibleGoodsService.pageMyGoods(pageDto, userId, status));
    }

    /**
     * 校验非遗人角色与登录态：网关鉴权通过后透传 X-User-Id / X-User-Role 可信头
     * 缺失用户ID视为未登录；非非遗人角色（普通用户 / 管理员）无商品发布管理权限
     * @param action 操作名称（发布 / 修改 / 删除），用于生成精准的拒绝提示
     */
    private void checkInheritor(Long userId, Integer role, String action) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
        if (role == null || role != ROLE_INHERITOR) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "你没有权限" + action + "非遗商品");
        }
    }

    /**
     * 校验管理员角色：网关鉴权通过后透传 X-User-Role 可信头，非0（管理员）拒绝操作
     */
    private void checkAdmin(Integer role) {
        if (role == null || role != ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可审核或管理商品");
        }
    }
}
