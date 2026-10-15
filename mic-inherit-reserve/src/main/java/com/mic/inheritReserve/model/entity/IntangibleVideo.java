package com.mic.inheritReserve.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学习视频表（传承预约-视频学习板块）：非遗人发布 + 管理员审核，前台仅展示已通过
 */
@Data
@TableName("intangible_video")
public class IntangibleVideo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布人ID（非遗人，关联 sys_user.id） */
    private Long publisherId;

    private String title;

    /** 视频简介 */
    private String content;

    /** 封面缩略图OSS地址（发布时自动截帧上传） */
    private String imgUrl;

    /** 视频OSS地址 */
    private String videoUrl;

    /** 视频时长（如 12:30） */
    private String duration;

    /** 审核状态：0待审核 1已通过 2已驳回 */
    private Integer auditStatus;

    /** 审核备注 / 驳回原因（管理员审核时写入） */
    private String auditRemark;

    /** 审核时间（管理员审核时写入） */
    private LocalDateTime auditTime;

    /** 审核管理员ID（关联 sys_user.id） */
    private Long auditorId;

    /** 板块信息类型（瞬态，不落库）：1视频学习（前端据此外显分类标签与视频/封面区块） */
    @TableField(exist = false)
    private Integer infoType;

    /** 发布人账号（管理端列表 LEFT JOIN sys_user 填充，非表字段） */
    @TableField(exist = false)
    private String publisherAccount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
