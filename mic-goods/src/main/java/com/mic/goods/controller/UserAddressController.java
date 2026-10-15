package com.mic.goods.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.model.dto.AddressSaveDTO;
import com.mic.goods.model.entity.UserAddress;
import com.mic.goods.service.UserAddressService;
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
 * 收货地址接口：列表查询、新增、编辑、删除
 * 全部接口需登录（网关鉴权后透传 X-User-Id 可信头），登录用户仅可操作本人地址
 */
@Tag(name = "收货地址接口")
@RestController
@RequestMapping("/api/goods/address")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    /**
     * 收货地址列表（登录用户）：默认地址在前
     */
    @Operation(summary = "收货地址列表（登录用户）：本人全部地址，默认地址在前")
    @GetMapping("/list")
    public Result<List<UserAddress>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(userAddressService.listMyAddress(userId));
    }

    /**
     * 新增收货地址（登录用户）：设为默认时自动替换原默认地址；首个地址自动设为默认
     */
    @Operation(summary = "新增收货地址（登录用户）：设为默认自动替换原默认地址，首个地址自动设为默认")
    @PostMapping
    public Result<Long> add(@Valid @RequestBody AddressSaveDTO dto,
                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(userAddressService.addAddress(dto, userId));
    }

    /**
     * 编辑收货地址（登录用户）：仅可编辑本人地址
     */
    @Operation(summary = "编辑收货地址（登录用户）：仅可编辑本人地址")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody AddressSaveDTO dto,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        userAddressService.updateAddress(id, dto, userId);
        return Result.ok();
    }

    /**
     * 删除收货地址（登录用户）：仅可删除本人地址（逻辑删除）
     */
    @Operation(summary = "删除收货地址（登录用户）：仅可删除本人地址")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        userAddressService.deleteAddress(id, userId);
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
