package com.mic.inheritReserve.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 论坛帖子发布 / 编辑入参DTO（登录用户）
 * 发帖即发即显（audit_status=1）；图片 / 视频先上传 OSS 再提交（图片为多图JSON数组字符串，与非遗资料存储约定一致）
 */
@Data
public class ForumPublishDTO implements Serializable {

    @NotBlank(message = "帖子标题不能为空")
    @Size(min = 2, max = 60, message = "标题长度2~60字符")
    private String title;

    @NotBlank(message = "帖子正文不能为空")
    @Size(max = 500, message = "正文不能超过500字符")
    private String content;

    /** 话题ID（可选，关联 inherit_forum_topic.id；选用时话题使用次数+1） */
    private Long topicId;

    /** 配图OSS地址：多图JSON数组字符串（可选） */
    @Size(max = 4096, message = "配图地址过长")
    private String imgUrl;

    /** 视频OSS地址（可选） */
    @Size(max = 1024, message = "视频地址不能超过1024字符")
    private String videoUrl;
}
