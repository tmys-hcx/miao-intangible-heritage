package com.mic.goods.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.model.dto.CartAddDTO;
import com.mic.goods.model.dto.CartUpdateDTO;
import com.mic.goods.model.vo.CartItemVO;
import com.mic.goods.service.ShoppingCartService;
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
 * 购物车接口：加入购物车、列表查询、修改数量、删除条目
 * 全部接口需登录（网关鉴权后透传 X-User-Id 可信头），登录用户仅可操作本人购物车
 * 购物车数据存储于 Redis（Hash：miao:cart:{userId}，field=商品ID），条目以商品ID作为标识
 */
@Tag(name = "购物车接口")
@RestController
@RequestMapping("/api/goods/cart")
@RequiredArgsConstructor
public class ShoppingCartController {

    private final ShoppingCartService shoppingCartService;

    /**
     * 加入购物车（登录用户）：同一商品重复加入时数量累加
     */
    @Operation(summary = "加入购物车（登录用户）：同一商品重复加入数量累加，超出库存/限购时提示")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody CartAddDTO dto,
                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        shoppingCartService.addCart(dto, userId);
        return Result.ok();
    }

    /**
     * 购物车列表（登录用户）
     */
    @Operation(summary = "购物车列表（登录用户）：本人购物车，携带商品名称/图片/价格等展示信息，最新加入在前")
    @GetMapping("/list")
    public Result<List<CartItemVO>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(shoppingCartService.listCart(userId));
    }

    /**
     * 修改购物车数量（登录用户）：按商品ID定位本人条目
     */
    @Operation(summary = "修改购物车数量（登录用户）：按商品ID修改本人购物车条目数量")
    @PutMapping("/{goodsId}")
    public Result<Void> updateNum(@PathVariable("goodsId") Long goodsId,
                                  @Valid @RequestBody CartUpdateDTO dto,
                                  @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        shoppingCartService.updateCartNum(goodsId, dto.getGoodsNum(), userId);
        return Result.ok();
    }

    /**
     * 删除购物车条目（登录用户）：按商品ID移除本人条目
     */
    @Operation(summary = "删除购物车条目（登录用户）：按商品ID删除本人购物车条目")
    @DeleteMapping("/{goodsId}")
    public Result<Void> delete(@PathVariable("goodsId") Long goodsId,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        shoppingCartService.deleteCart(goodsId, userId);
        return Result.ok();
    }

    /**
     * 校验登录态：网关鉴权通过后透传 X-User-Id 可信头，缺失视为未登录
     */
    private void checkLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
    }
}
