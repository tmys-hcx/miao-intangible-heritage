package com.mic.agent.study.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 论坛回答草稿入参DTO（/api/agent/study/forum/draft）：
 * 传入论坛帖子ID，Agent 读取知识库资料自动生成回答草稿，供用户修改后直接发布到论坛
 */
@Data
public class ForumDraftDTO implements Serializable {

    /** 论坛帖子ID（须为前台可见的已通过帖子） */
    @NotNull(message = "帖子ID不能为空")
    private Long postId;
}
