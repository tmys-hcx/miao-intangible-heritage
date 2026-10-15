package com.mic.inheritReserve.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛评论点赞表（传承与学习-论坛）：登录用户可点赞 / 取消点赞评论（is_deleted 0有效 1已取消）；
 * 同一用户对同一评论仅保留一条物理记录，取消后再次点赞恢复原记录，避免唯一键冲突（底层评论沿用 reply 命名）
 */
@Data
@TableName("inherit_forum_reply_like")
public class ForumReplyLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点赞用户ID（关联 sys_user.id） */
    private Long userId;

    /** 评论（回复）ID（关联 inherit_forum_reply.id） */
    private Long replyId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
