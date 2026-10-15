package com.mic.common.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 传承资料检索结果VO（智能助手 Agent「资料检索问答」使用）：
 * mic-inherit-reserve 按关键词聚合检索【访谈资料 / 知识窗口 / 学习视频简介】后返回，
 * mic-agent 侧 Feign 接收该结构，再由 Agent 工具整理为模型可读文本
 */
@Data
public class InheritSearchVO implements Serializable {

    /** 信息类型：1视频学习 3访谈资料 4知识窗口（与前台板块命名一致，Agent 据此标注来源） */
    private Integer infoType;

    /** 资料ID（所属板块的内容ID） */
    private Long id;

    /** 资料标题 */
    private String title;

    /** 纯文本片段（正文 HTML 已剥离并截断，供 Agent 引用作答） */
    private String snippet;
}
