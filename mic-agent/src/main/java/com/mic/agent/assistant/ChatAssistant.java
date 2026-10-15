package com.mic.agent.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * 智能助手对话接口（双主题共用：传承与学习 / 非遗商品，由系统提示词按用户意图路由）：
 * 装配方式——由 AssistantConfig 通过 AiServices.builder 编程式构建（不再使用声明式 @AiService 注解）：
 * 1、chatModel / streamingChatModel：deepseekChatModel / deepseekStreamingChatModel Bean
 *    （OpenAI 兼容协议指向 DeepSeek，见 application.yml）；
 * 2、systemMessageProvider：每次请求经 AgentPromptProvider 完整加载 prompt/agent-prompt.txt 全文，
 *    作为 SystemMessage 置于消息数组第一位（不截断、不删减；
 *    组装顺序：系统提示 → Redis 恢复的历史对话 → 当前用户消息）；
 * 3、chatMemoryProvider：Redis 持久化会话记忆（@MemoryId 隔离多轮上下文，memoryId 由 ChatController 传入：
 *    登录用户为 JWT token、游客为 guest:sessionId）；
 * 4、tools：ShopTools / OrderTools / MaterialSearchTool 工具集
 *    （经 OpenFeign 调 mic-goods / mic-user / mic-inherit-reserve 获取真实业务数据与非遗资料，禁止编造）；
 * 5、当前用户消息头部的强制指令前缀由 ChatController 动态追加（见 COMPLIANCE_INSTRUCTION）。
 * 说明：接口只声明对话方法；对外 HTTP 端点（/chat、/chat/stream）由 controller 层注入本接口转发。
 */
public interface ChatAssistant {

    /** 同步对话：返回完整回复（多轮上下文按 memoryId 隔离） */
    String chat(@MemoryId String memoryId, @UserMessage String message);

    /** 流式对话：返回 token 流（SSE 输出的 HTTP 端点由 controller 层封装） */
    Flux<String> chatStream(@MemoryId String memoryId, @UserMessage String message);
}