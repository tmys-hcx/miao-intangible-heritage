package com.mic.culture.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("culture_comment")
public class CultureComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "非遗文化内容ID不能为空")
    private Long cultureId;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 所属顶级评论ID（0为顶级评论；二级回复统一指向其顶级评论） */
    @NotNull(message = "所属顶级评论ID不能为空")
    private Long parentId;

    /** 被回复用户ID（二级回复定向标注；顶级评论为空） */
    private Long replyUserId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 300, message = "评论内容不能超过300字符")
    private String commentContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
