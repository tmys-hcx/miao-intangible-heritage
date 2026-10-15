package com.mic.inheritReserve.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛帖子点赞表（传承与学习-论坛）：登录用户可点赞 / 取消点赞帖子（is_deleted 0有效 1已取消）；
 * 同一用户对同一帖子仅保留一条物理记录，取消后再次点赞恢复原记录，避免唯一键冲突（与 user_like_collect 一致）
 */
@Data
@TableName("inherit_forum_like")
public class ForumLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点赞用户ID（关联 sys_user.id） */
    private Long userId;

    /** 帖子ID（关联 inherit_forum_post.id） */
    private Long postId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
