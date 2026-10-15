package com.mic.inheritReserve.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛话题表（传承与学习-论坛板块）：话题名 + 使用次数（发帖选用时 +1，按热度排序展示）
 */
@Data
@TableName("inherit_forum_topic")
public class ForumTopic {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 话题名（不含#号，唯一） */
    private String name;

    /** 使用次数（帖子选用该话题的次数，用于热度排序） */
    private Integer useCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
