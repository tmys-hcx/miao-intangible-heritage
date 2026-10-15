package com.mic.inheritReserve.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 旧非遗传承信息表（过渡保留）：原 intangible_inherit 表已按业务拆分为
 * intangible_video / intangible_interview / intangible_knowledge 三张表；
 * 本实体与 IntangibleInheritMapper 仅为兼容 Service 继承结构保留，
 * 不再执行任何实际数据操作，旧表确认无引用后可一并移除。
 */
@Data
@TableName("intangible_inherit")
public class IntangibleInherit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布人ID（关联 sys_user.id） */
    private Long publisherId;

    private String title;

    /** 内容 */
    private String content;

    /** 信息类型：1视频学习 3访谈资料 4知识窗口（2论坛已拆分至 inherit_forum_post） */
    private Integer infoType;

    /** 图片OSS地址（多图英文逗号隔开） */
    private String imgUrl;

    /** 视频OSS地址 */
    private String videoUrl;

    /** 视频时长（学习视频专用，如 12:30） */
    private String duration;

    /** 审核状态：0待审核 1已通过 2已驳回 */
    private Integer auditStatus;

    /** 审核备注 / 驳回原因 */
    private String auditRemark;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核管理员ID（关联 sys_user.id） */
    private Long auditorId;

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
