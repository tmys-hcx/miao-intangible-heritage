package com.mic.culture.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评论发布入参DTO（发表评论 / 回复共用）
 */
@Data
public class CultureCommentDTO {

    /** 非遗资料ID */
    @NotNull(message = "非遗文化内容ID不能为空")
    private Long cultureId;

    /** 评论内容 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 300, message = "评论内容不能超过300字符")
    private String commentContent;

    /** 被回复的评论ID（0或空为发表顶级评论；否则为回复该评论，后端归一化到所属顶级评论） */
    private Long parentId;
}
