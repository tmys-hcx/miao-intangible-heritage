package com.mic.goods.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("goods_category")
public class GoodsCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "商品分类名称不能为空")
    @Size(min = 1, max = 50, message = "分类名称长度1~50字符")
    private String categoryName;

    @Size(max = 200, message = "分类描述不能超过200字符")
    private String categoryDesc;

    // 父分类ID，一级分类parentId传0，允许前端传0，不能为null
    @NotNull(message = "父分类ID不能为空")
    private Long parentId;

    @NotNull(message = "排序号不能为空")
    @Min(value = 0, message = "排序号不能小于0")
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    /** 二级子分类列表：仅分页接口组装返回，不映射数据库字段 */
    @TableField(exist = false)
    private List<GoodsCategory> children;
}
