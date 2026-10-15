package com.mic.agent.study.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 论坛帖子详情（Agent 侧接收结构）：由 mic-inherit-reserve GET /api/inherit/forum/{id} 返回的
 * ForumPost 实体 JSON 收敛而来，仅保留辅助答疑生成回答草稿需要的字段
 */
@Data
public class ForumPostDTO implements Serializable {

    private Long id;

    private String title;

    private String content;
}
