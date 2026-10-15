package com.mic.agent.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

/**
 * 智能助手对话入参DTO（/api/agent/chat 与 /api/agent/chat/stream 共用）：
 * theme 指定助手主题（study 传承与学习 / shop 非遗商品），缺省走基础问答；
 * sessionId 用于多轮上下文隔离（不传时服务端生成随机值，按单轮处理）
 */
@Data
public class AgentChatDTO implements Serializable {
    /** 助手主题：study 传承与学习（资料检索问答） / shop 非遗商品（接入中）；缺省为基础问答，仅用于标记日志，实际意图由系统prompt路由 */
    private String theme;
    /** 会话ID（前端生成并保持，同一会话多轮共享上下文；缺省时后端生成随机值） */
    private String sessionId;
    /** 用户消息内容 */
    @NotBlank(message = "消息内容不能为空")
    private String message;
}