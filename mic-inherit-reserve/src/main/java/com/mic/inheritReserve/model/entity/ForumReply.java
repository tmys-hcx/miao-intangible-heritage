package com.mic.inheritReserve.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛回复表（传承与学习-论坛板块）：两级对话结构（与 culture_comment 一致），
 * parent_id 统一指向所属顶级回复（0 为顶级），回复他人时记录 reply_user_id（定向标注）；
 * 回复作者本人或帖子作者可删除（逻辑删除），删除顶级回复连带删除其二级回复
 */
@Data
@TableName("inherit_forum_reply")
public class ForumReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 帖子ID（关联 inherit_forum_post.id） */
    private Long postId;

    /** 回复用户ID（关联 sys_user.id） */
    private Long userId;

    /** 所属顶级回复ID（0 为顶级回复；二级回复统一指向其顶级回复） */
    private Long parentId;

    /** 被回复用户ID（二级回复定向标注，顶级回复为空） */
    private Long replyUserId;

    /** 回复内容 */
    private String replyContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
