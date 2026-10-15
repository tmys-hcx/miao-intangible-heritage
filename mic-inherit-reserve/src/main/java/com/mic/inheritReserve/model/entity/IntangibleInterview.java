package com.mic.inheritReserve.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 访谈资料表（传承预约-访谈资料板块）：口述实录，管理员维护直发（无审核环节）
 */
@Data
@TableName("intangible_interview")
public class IntangibleInterview {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 发布人ID（管理员，关联 sys_user.id） */
    private Long publisherId;

    private String title;

    /** 正文（HTML富文本，图文穿插） */
    private String content;

    /** 封面/配图OSS地址：单图URL或多图（前端 parseImgList 兼容解析） */
    private String imgUrl;

    /** 板块信息类型（瞬态，不落库）：3访谈资料（管理端列表/编辑回填使用） */
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
