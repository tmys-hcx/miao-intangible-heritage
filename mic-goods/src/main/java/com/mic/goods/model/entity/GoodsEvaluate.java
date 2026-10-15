package com.mic.goods.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("goods_evaluate")
public class GoodsEvaluate {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 500, message = "评价内容最多500字符")
    private String evaluateContent;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "最低1星")
    @Max(value = 5, message = "最高5星")
    private Integer score;

    /** 评价人昵称（非表字段）：自定义 SQL 关联 sys_user 回填，展示"nickname：内容"格式 */
    @TableField(exist = false)
    private String userNickname;

    /** 追问/回复列表（非表字段）：评价分页时批量回填，按创建时间正序 */
    @TableField(exist = false)
    private List<GoodsEvaluateReply> replies;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
