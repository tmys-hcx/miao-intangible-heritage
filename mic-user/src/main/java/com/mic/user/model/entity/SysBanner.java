package com.mic.user.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_banner")
public class SysBanner {

    /** 轮播类型：1图片 */
    public static final int TYPE_IMAGE = 1;

    /** 轮播类型：2视频 */
    public static final int TYPE_VIDEO = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "轮播媒体地址不能为空")
    @Size(max = 500, message = "媒体地址长度不能超过500字符")
    private String bannerImg;

    /** 轮播类型：1图片 2视频（默认图片） */
    @Min(value = 1, message = "轮播类型不合法")
    @Max(value = 2, message = "轮播类型不合法")
    private Integer bannerType = TYPE_IMAGE;

    @Size(max = 200, message = "轮播图描述不能超过200字符")
    private String bannerDesc;

    @NotNull(message = "排序号不能为空")
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}

