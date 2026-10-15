package com.mic.agent.config;

import com.mic.agent.assistant.ChatAssistant;
import com.mic.agent.shop.tool.OrderTools;
import com.mic.agent.shop.tool.ShopTools;
import com.mic.agent.study.tool.MaterialSearchTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智能助手装配配置（编程式 AiServices.builder 构建，替代声明式 @AiService 注解）：
 * 目的——把"系统提示词完整注入 + 消息组装顺序"从框架隐式行为改为显式可控机制：
 * 1、systemMessageProvider：每次请求回调，经 AgentPromptProvider 实时读取 agent-prompt.txt 完整全文，
 *    作为 SystemMessage 固定置于消息数组第一位（LangChain4j 组装顺序：系统提示 → Redis 恢复的历史对话 → 当前用户消息）；
 * 2、chatMemoryProvider：Redis 持久化对话记忆（chatMemory Bean，按 memoryId 从 PersistentChatMemoryStore 恢复历史）；
 * 3、tools：挂载 ShopTools / OrderTools / MaterialSearchTool 三个 @Tool 工具集
 *    （经 OpenFeign 调 mic-goods / mic-user / mic-inherit-reserve 获取真实业务数据与非遗资料，禁止编造数据）；
 * 4、当前用户消息头部的强制指令前缀由 ChatController 动态追加（见 COMPLIANCE_INSTRUCTION）。
 */
@Configuration
@RequiredArgsConstructor
public class AssistantConfig {

    private final AgentPromptProvider agentPromptProvider;

    @Bean
    public ChatAssistant chatAssistant(OpenAiChatModel chatModel,
                                       OpenAiStreamingChatModel streamingChatModel,
                                       ChatMemoryProvider chatMemoryProvider,
                                       ShopTools shopTools,
                                       OrderTools orderTools,
                                       MaterialSearchTool materialSearchTool) {
        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                // 每次聊天请求动态提供系统提示：完整读取 agent-prompt.txt 全文，不截断、不删减
                .systemMessageProvider(memoryId -> agentPromptProvider.loadFullText())
                .tools(shopTools, orderTools, materialSearchTool)
                // 工具参数转换失败 / 工具内部异常时不中断整轮对话：改为把错误文本作为工具结果
                // 回给模型（模型可自纠后重试），同时保证 assistant 的每个 tool_call 都有对应
                // tool 响应写入记忆——否则历史断裂后该会话被 DeepSeek 永久 400 拒绝
                .toolArgumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "工具参数格式不正确（" + error.getMessage() + "），请检查参数类型后重新调用该工具。"))
                .toolExecutionErrorHandler((error, context) -> ToolErrorHandlerResult.text(
                        "工具执行出现异常（" + error.getMessage() + "），请稍后重试或换用其它方式获取数据。"))
                .build();
    }
}
