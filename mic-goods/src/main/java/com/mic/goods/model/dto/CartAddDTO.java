package com.mic.goods.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 加入购物车入参DTO
 * 说明：用户ID由服务端从网关透传头获取；同一用户同一商品重复加入时数量累加
 */
@Data
public class CartAddDTO implements Serializable {

    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    @NotNull(message = "商品数量不能为空")
    @Positive(message = "商品数量必须大于0")
    private Integer goodsNum;
}
