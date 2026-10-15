package com.mic.goods.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("intangible_goods")
public class IntangibleGoods {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "发布人ID不能为空")
    private Long publisherId;

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

    @NotNull(message = "商品状态不能为空")
    private Integer status;

    /** 审核备注（非表字段）：审核拒绝时为不合格原因，出参时从 Redis 审核记录回填 */
    @TableField(exist = false)
    private String auditRemark;

    /** 审核时间（非表字段）：yyyy-MM-dd HH:mm:ss，出参时从 Redis 审核记录回填 */
    @TableField(exist = false)
    private String auditTime;

    /** 发布人账号（非表字段）：管理端列表自定义 SQL 关联 sys_user 回填，展示发布人替代ID */
    @TableField(exist = false)
    private String publisherAccount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
