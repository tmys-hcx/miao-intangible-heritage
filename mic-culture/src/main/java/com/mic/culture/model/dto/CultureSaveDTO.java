package com.mic.culture.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 非遗资料发布 / 修改入参DTO（对齐接口设计文档 CultureSaveDTO(title,content,img_url,video_url)）
 */
@Data
public class CultureSaveDTO implements Serializable {

    @NotBlank(message = "非遗资料标题不能为空")
    @Size(min = 2, max = 120, message = "标题长度2~120字符")
    private String title;

    @NotBlank(message = "非遗资料内容不能为空")
    private String content;

    /** 图片链接：多张图用JSON数组字符串存储（与 intangible_culture.img_url 存储约定一致）；与视频至少上传其一 */
    @Size(max = 2048, message = "图片链接不能超过2048字符")
    private String imgUrl;

    /** 视频链接（与图片至少上传其一：无图片时必传；无视频传空串或省略即可） */
    @Size(max = 512, message = "视频链接不能超过512字符")
    private String videoUrl;

    /** 视频封面缩略图OSS地址（发布端上传视频成功后本地截帧自动生成；无图片时卡片与详情页展示，可空） */
    @Size(max = 1024, message = "视频封面地址不能超过1024字符")
    private String coverUrl;

    /**
     * 联合校验：图片与视频至少上传其一（发布 / 修改共用本 DTO，故原 imgUrl 必填校验放宽为联合校验）
     * 无图片时 imgUrl 可能为 null / 空串 / "[]"（前端对空列表 JSON.stringify 的结果），均视为无图
     */
    @AssertTrue(message = "请至少上传一张图片或一个视频")
    @JsonIgnore
    public boolean isMediaPresent() {
        boolean hasImage = imgUrl != null && !imgUrl.isBlank() && !"[]".equals(imgUrl.trim());
        boolean hasVideo = videoUrl != null && !videoUrl.isBlank();
        return hasImage || hasVideo;
    }
}
