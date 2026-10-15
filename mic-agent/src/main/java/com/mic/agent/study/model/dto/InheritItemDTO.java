package com.mic.agent.study.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 传承信息详情（Agent 侧接收结构）：由 mic-inherit-reserve GET /api/inherit/{id} 返回的
 * 三张拆分局表实体 JSON（视频学习 / 访谈资料 / 知识窗口）收敛而来，仅保留推荐功能需要的字段；
 * content：视频学习为视频简介、访谈资料 / 知识窗口为正文（HTML 富文本，Agent 侧拼入提示词前需做纯文本化处理）
 */
@Data
public class InheritItemDTO implements Serializable {

    private Long id;

    private String title;

    private String content;
}
