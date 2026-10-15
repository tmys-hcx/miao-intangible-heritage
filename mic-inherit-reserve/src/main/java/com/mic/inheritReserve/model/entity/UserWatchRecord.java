package com.mic.inheritReserve.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学习视频观看记录表（传承预约-视频学习板块）：非遗人 / 普通用户完整观看后写入
 * 同一用户同一视频仅保留一条（唯一键 uk_user_video），重复完整观看仅刷新最近观看时间
 */
@Data
@TableName("user_watch_record")
public class UserWatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（关联 sys_user.id，非遗人 / 普通用户） */
    private Long userId;

    /** 学习视频ID（关联 intangible_video.id） */
    private Long videoId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最近完整观看时间（重复观看由 upsert 刷新） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
