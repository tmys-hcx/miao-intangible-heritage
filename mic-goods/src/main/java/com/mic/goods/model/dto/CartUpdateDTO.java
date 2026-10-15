package com.mic.goods.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改购物车数量入参DTO
 */
@Data
public class CartUpdateDTO implements Serializable {

    @NotNull(message = "商品数量不能为空")
    @Positive(message = "商品数量必须大于0")
    private Integer goodsNum;
}
