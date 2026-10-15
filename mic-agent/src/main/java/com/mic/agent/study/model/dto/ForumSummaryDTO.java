package com.mic.agent.study.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 论坛智能总结入参：论坛搜索框关键词（对检索命中的帖子与帖内评论生成总结）
 */
@Data
public class ForumSummaryDTO implements Serializable {

    /** 搜索关键词（与论坛搜索一致：标题 / 正文模糊匹配） */
    @NotBlank(message = "搜索关键词不能为空")
    private String keyword;
}
