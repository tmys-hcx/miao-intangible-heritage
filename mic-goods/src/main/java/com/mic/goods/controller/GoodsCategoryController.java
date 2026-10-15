package com.mic.goods.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.model.dto.GoodsCategorySaveDTO;
import com.mic.goods.model.entity.GoodsCategory;
import com.mic.goods.service.GoodsCategoryService;
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
 * 商品分类管理：前台列表查询（免登录） + 管理员增删改
 */
@Tag(name = "商品分类接口")
@RestController
@RequestMapping("/api/goods/category")
@RequiredArgsConstructor
public class GoodsCategoryController {

    /** 角色：0管理员 */
    private static final int ROLE_ADMIN = 0;

    private final GoodsCategoryService goodsCategoryService;

    /**
     * 商品分类列表（免登录）：parentId为空查全部，传0查一级分类，传ID查其子分类
     */
    @Operation(summary = "商品分类列表（免登录）：parentId为空查全部，传0查一级分类，传ID查其子分类")
    @GetMapping("/list")
    public Result<List<GoodsCategory>> list(@RequestParam(value = "parentId", required = false) Long parentId) {
        return Result.ok(goodsCategoryService.listCategories(parentId));
    }

    /**
     * 商品分类分页（免登录）：一级分类按创建时间倒序分页，携带二级子分类；支持按分类名称模糊查询
     */
    @Operation(summary = "商品分类分页（免登录）：按创建时间倒序，默认每页10条，可传categoryName模糊查询")
    @GetMapping("/page")
    public Result<PageResult<GoodsCategory>> page(PageDto pageDto,
                                                  @RequestParam(value = "categoryName", required = false) String categoryName) {
        return Result.ok(goodsCategoryService.pageCategories(pageDto, categoryName));
    }

    /**
     * 新增商品分类（管理员）
     */
    @Operation(summary = "新增商品分类（管理员）")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody GoodsCategorySaveDTO dto,
                             @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        goodsCategoryService.saveCategory(dto);
        return Result.ok();
    }

    /**
     * 修改商品分类（管理员）
     */
    @Operation(summary = "修改商品分类（管理员）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody GoodsCategorySaveDTO dto,
                               @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        goodsCategoryService.updateCategory(id, dto);
        return Result.ok();
    }

    /**
     * 删除商品分类（管理员，逻辑删除）
     */
    @Operation(summary = "删除商品分类")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id,
                               @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        goodsCategoryService.deleteCategory(id);
        return Result.ok();
    }

    /**
     * 校验管理员角色：网关鉴权通过后透传 X-User-Role 可信头，非0（管理员）拒绝操作
     */
    private void checkAdmin(Integer role) {
        if (role == null || role != ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可管理商品分类");
        }
    }
}
