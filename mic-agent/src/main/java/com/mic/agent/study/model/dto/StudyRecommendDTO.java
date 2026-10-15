package com.mic.agent.study.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 学习内容推荐入参DTO（/api/agent/study/recommend）：
 * 传入用户当前浏览的传承信息（信息类型 + 内容ID），Agent 据此检索资料库，
 * 推荐配套学习视频、访谈实录
 */
@Data
public class StudyRecommendDTO implements Serializable {

    /** 当前浏览条目的信息类型：1视频学习 3访谈资料 4知识窗口（与前台板块命名一致） */
    @NotNull(message = "信息类型不能为空")
    private Integer infoType;

    /** 当前浏览条目的内容ID（所属板块的内容ID） */
    @NotNull(message = "内容ID不能为空")
    private Long id;
}
