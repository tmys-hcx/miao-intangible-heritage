package com.mic.agent.study.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 论坛评论条目（Agent 侧接收结构）：由 mic-inherit-reserve GET /api/inherit/forum/reply/page 返回的
 * ForumReplyVO JSON 收敛而来（顶级评论分页，二级回复嵌在 replies），仅保留论坛智能总结需要的字段
 */
@Data
public class ForumReplyItemDTO implements Serializable {

    /** 评论ID */
    private Long id;

    /** 帖子ID */
    private Long postId;

    /** 评论内容 */
    private String replyContent;

    /** 评论者昵称（联表 sys_user 回填） */
    private String nickname;

    /** 二级回复列表（仅顶级评论有值） */
    private List<ForumReplyItemDTO> replies;
}
