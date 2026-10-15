package com.mic.culture.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_like_collect")
public class UserLikeCollect {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "非遗文化ID不能为空")
    private Long cultureId;

    @NotNull(message = "类型不能为空")
    @Min(value = 1, message = "类型只能为1点赞，2收藏")
    @Max(value = 2, message = "类型只能为1点赞，2收藏")
    private Integer type;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
