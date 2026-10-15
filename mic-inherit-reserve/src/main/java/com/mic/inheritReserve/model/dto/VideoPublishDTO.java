package com.mic.inheritReserve.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 学习视频发布入参DTO（非遗人发布）
 * 提交后进入待审核（audit_status=0），管理员审核通过后在前台传承预约-视频学习板块展示
 */
@Data
public class VideoPublishDTO implements Serializable {

    @NotBlank(message = "视频标题不能为空")
    @Size(min = 2, max = 60, message = "标题长度2~60字符")
    private String title;

    @NotBlank(message = "视频链接不能为空")
    @Size(max = 512, message = "视频链接不能超过512字符")
    private String videoUrl;

    /** 视频封面缩略图OSS地址（发布端上传成功后自动截帧生成，可选） */
    @Size(max = 1024, message = "封面地址不能超过1024字符")
    private String imgUrl;

    /** 视频时长（可选，如 12:30） */
    @Size(max = 16, message = "视频时长不能超过16字符")
    private String duration;

    /** 视频简介（可选） */
    @Size(max = 80, message = "视频简介不能超过80字符")
    private String content;
}
