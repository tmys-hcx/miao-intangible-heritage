package com.mic.goods.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 非遗商品发布/修改入参DTO（对应 intangible_goods 表字段）
 * 说明：发布人ID、审核状态由服务端填充；校验口径与实体 IntangibleGoods 保持一致
 */
@Data
public class GoodsSaveDTO implements Serializable {

    @NotNull(message = "商品分类ID不能为空")
    private Long categoryId;

    @NotBlank(message = "商品名称不能为空")
    @Size(min = 2, max = 100, message = "商品名称长度2~100字符")
    private String goodsName;

    @Size(max = 1000, message = "商品描述不能超过1000字符")
    private String goodsDesc;

    @NotBlank(message = "商品图片地址不能为空")
    @Size(max = 255, message = "图片地址长度不能超过255字符")
    private String goodsImg;

    @NotNull(message = "商品价格不能为空")
    @PositiveOrZero(message = "商品价格不能为负数")
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @PositiveOrZero(message = "库存不能为负数")
    private Integer stock;

    @NotNull(message = "发货时间不能为空")
    @Positive(message = "发货时间必须大于0")
    private Integer deliveryTime;

    @NotNull(message = "限购数量不能为空")
    @Positive(message = "单次限购数量必须大于0")
    private Integer limitBuyNum;
}
