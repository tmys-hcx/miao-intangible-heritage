package com.mic.inheritReserve.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 论坛回复VO（回复分页出参）：
 * 联表 sys_user 回填回复者昵称 / 头像 / 角色；二级回复额外回填被回复人昵称；
 * isAuthor 标记回复者是否为帖子作者（前端展示「作者」标注）；
 * userRole=1（非遗人）时昵称后展示「非遗传承人」标注
 */
@Data
public class ForumReplyVO implements Serializable {

    /** 回复ID */
    private Long id;

    /** 帖子ID */
    private Long postId;

    /** 回复者用户ID */
    private Long userId;

    /** 所属顶级回复ID（0为顶级回复；二级回复统一指向其顶级回复） */
    private Long parentId;

    /** 被回复用户ID（二级回复定向标注；顶级回复为空） */
    private Long replyUserId;

    /** 回复内容 */
    private String replyContent;

    /** 回复时间 */
    private LocalDateTime createTime;

    /** 回复者昵称（联表 sys_user 回填） */
    private String nickname;

    /** 回复者头像（联表 sys_user 回填，可能为空） */
    private String avatar;

    /** 回复者角色（联表 sys_user 回填：0管理员 1非遗人 2普通用户；非遗人前端展示「非遗传承人」标注） */
    private Integer userRole;

    /** 被回复人昵称（二级回复定向展示；顶级回复为空） */
    private String replyNickname;

    /** 点赞数（评论列表统计 inherit_forum_reply_like 填充） */
    private Long likeCount;

    /** 是否帖子作者本人的回复（作者标注） */
    private Boolean isAuthor;

    /** 二级回复列表（仅顶级回复有值） */
    private List<ForumReplyVO> replies;
}
