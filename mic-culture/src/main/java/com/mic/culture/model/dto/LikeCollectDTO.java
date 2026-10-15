package com.mic.culture.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 点赞 / 收藏操作DTO
 * 点赞（POST /like）、收藏（POST /collect）仅需传 cultureId，类型由接口语义决定；
 * 取消（DELETE /likecollect）需传 cultureId + type
 */
@Data
public class LikeCollectDTO {

    /** 非遗资料ID */
    @NotNull(message = "非遗资料ID不能为空")
    private Long cultureId;

    /** 类型：1点赞 / 2收藏（取消接口必填，点赞 / 收藏接口由接口语义决定） */
    @Min(value = 1, message = "类型只能为1点赞，2收藏")
    @Max(value = 2, message = "类型只能为1点赞，2收藏")
    private Integer type;
}
