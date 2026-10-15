package com.mic.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("intangible_news")
public class IntangibleNews {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "发布管理员ID不能为空")
    private Long adminId;

    @NotBlank(message = "资讯标题不能为空")
    @Size(min = 2, max = 120, message = "标题长度需要在2‑120字符之间")
    private String title;

    @NotBlank(message = "资讯内容不能为空")
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
