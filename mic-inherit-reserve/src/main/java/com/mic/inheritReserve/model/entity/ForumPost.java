package com.mic.inheritReserve.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 论坛帖子表（传承与学习-论坛板块）：登录用户发布，发帖 / 编辑后进入待审核（audit_status=0），
 * 管理员审核通过后前台展示；帖子经 topicId 关联话题表（inherit_forum_topic）；作者本人可编辑 / 删除（逻辑删除）
 */
@Data
@TableName("inherit_forum_post")
public class ForumPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发帖人ID（关联 sys_user.id） */
    private Long publisherId;

    private String title;

    /** 帖子正文 */
    private String content;

    /** 话题ID（关联 inherit_forum_topic.id，NULL 为无话题） */
    private Long topicId;

    /** 配图OSS地址：多图JSON数组字符串（前端 parseImgList 兼容解析） */
    private String imgUrl;

    /** 视频OSS地址（NULL 为无视频） */
    private String videoUrl;

    /** 审核状态：0待审核 1已通过 2已驳回（当前发帖即通过，字段保留待后续启用审核制） */
    private Integer auditStatus;

    /** 审核备注 / 驳回原因（审核制启用后由管理员写入） */
    private String auditRemark;

    /** 审核时间（审核制启用后由管理员写入） */
    private LocalDateTime auditTime;

    /** 审核管理员ID（关联 sys_user.id） */
    private Long auditorId;

    /** 作者昵称（公开列表 LEFT JOIN sys_user 填充，非表字段） */
    @TableField(exist = false)
    private String publisherNickname;

    /** 作者头像OSS地址（公开列表 / 详情 LEFT JOIN sys_user 填充，非表字段） */
    @TableField(exist = false)
    private String publisherAvatar;

    /** 话题名（公开列表 LEFT JOIN inherit_forum_topic 填充，非表字段） */
    @TableField(exist = false)
    private String topicName;

    /** 回复数（公开列表统计 inherit_forum_reply 填充，非表字段） */
    @TableField(exist = false)
    private Long replyCount;

    /** 点赞数（列表 / 详情统计 inherit_forum_like 填充，非表字段） */
    @TableField(exist = false)
    private Long likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
