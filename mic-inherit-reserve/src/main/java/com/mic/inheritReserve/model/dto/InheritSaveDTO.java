package com.mic.inheritReserve.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 传承信息新增 / 编辑入参DTO（管理员维护）
 */
@Data
public class InheritSaveDTO implements Serializable {

    /** 信息类型：3访谈资料 4知识窗口 */
    @NotNull(message = "信息类型不能为空")
    private Integer infoType;

    @NotBlank(message = "传承信息标题不能为空")
    @Size(min = 2, max = 60, message = "标题长度2~60字符")
    private String title;

    @NotBlank(message = "传承信息内容不能为空")
    private String content;

    /** 图片链接：多张图用JSON数组字符串存储（与前端 parseImgList 解析约定一致） */
    @Size(max = 2048, message = "图片链接不能超过2048字符")
    private String imgUrl;
}
