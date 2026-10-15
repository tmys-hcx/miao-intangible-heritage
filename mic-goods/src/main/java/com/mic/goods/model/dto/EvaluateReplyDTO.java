package com.mic.goods.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 评价追问/回复入参DTO（双向对话）
 * 说明：用户ID由服务端从网关透传头获取；任何登录用户可追问他人评价，
 *      任何登录用户可回复追问（追问者与被追问者可来回对话，同一接口）
 */
@Data
public class EvaluateReplyDTO implements Serializable {

    @NotNull(message = "评价ID不能为空")
    private Long evaluateId;

    /** 父级追问ID（可空）：空=对评价的直接追问；非空=回复该追问（服务端校验必须为一级追问） */
    private Long parentReplyId;

    /** 被回复用户ID（可空）：回复追问/回复时记录，展示"回复 xxx"；为空时服务端按父级追问作者兜底 */
    private Long replyToUserId;

    @NotBlank(message = "追问/回复内容不能为空")
    @Size(max = 500, message = "追问/回复内容最多500字符")
    private String replyContent;
}
