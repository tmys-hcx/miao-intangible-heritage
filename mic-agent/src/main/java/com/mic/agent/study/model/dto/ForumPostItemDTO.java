package com.mic.agent.study.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 论坛帖子条目（Agent 侧接收结构）：由 mic-inherit-reserve GET /api/inherit/forum/page 返回的
 * ForumPost 实体 JSON 收敛而来，仅保留论坛智能总结需要的字段
 */
@Data
public class ForumPostItemDTO implements Serializable {

    /** 帖子ID */
    private Long id;

    private String title;

    /** 帖子正文 */
    private String content;

    /** 作者昵称（公开分页联表 sys_user 填充） */
    private String publisherNickname;

    /** 评论数（公开分页统计 inherit_forum_reply 填充） */
    private Long replyCount;
}
