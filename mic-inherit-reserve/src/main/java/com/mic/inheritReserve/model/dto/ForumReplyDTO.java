package com.mic.inheritReserve.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 论坛评论发布入参DTO（发表评论 / 评论他人共用）
 */
@Data
public class ForumReplyDTO implements Serializable {

    /** 帖子ID */
    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    /** 评论内容 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容不能超过500字符")
    private String replyContent;

    /** 被回复的评论ID（0或空为发表顶级评论；否则为评论该条，后端归一化到所属顶级评论） */
    private Long parentId;
}
