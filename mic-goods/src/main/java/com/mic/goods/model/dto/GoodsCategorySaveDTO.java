package com.mic.goods.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品分类新增/修改入参DTO（对应 goods_category 表字段）
 * 说明：parentId=0 代表一级分类；与实体 GoodsCategory 校验口径保持一致
 */
@Data
public class GoodsCategorySaveDTO implements Serializable {

    @NotBlank(message = "分类名称不能为空")
    @Size(min = 1, max = 50, message = "分类名称长度1~50字符")
    private String categoryName;

    @Size(max = 200, message = "分类描述不能超过200字符")
    private String categoryDesc;

    @NotNull(message = "父分类ID不能为空")
    @Min(value = 0, message = "父分类ID不能小于0")
    private Long parentId;

    @NotNull(message = "排序号不能为空")
    @Min(value = 0, message = "排序号不能小于0")
    private Integer sort;
}
