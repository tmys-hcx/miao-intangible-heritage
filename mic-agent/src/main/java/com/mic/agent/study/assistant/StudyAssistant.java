package com.mic.agent.study.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * 传承与学习助手（资料检索问答）：
 * 声明式装配——对话模型走 ChatModelConfig 显式装配的 deepseekChatModel / deepseekStreamingChatModel，
 * 记忆走 ChatMemoryConfig 的 chatMemory（Redis 持久化，@MemoryId 隔离多轮上下文），
 * 工具挂载 MaterialSearchTool（Bean 名 materialSearchTool）：回答非遗知识前先检索
 * mic-inherit-reserve 的【访谈资料 / 知识窗口 / 视频简介】，基于检索结果作答；
 * 找不到资料时按系统提示词（resources/prompts/study-system-prompt.txt）如实告知无相关内容。
 * 说明：对外 HTTP 端点（/api/agent/chat 的 study 主题）由 ChatController 按 theme 分流到本接口。
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "deepseekChatModel",
        streamingChatModel = "deepseekStreamingChatModel",
        chatMemoryProvider = "chatMemory",
        tools = {"materialSearchTool"}
)
public interface StudyAssistant {

    /** 同步对话（含资料检索工具调用）：返回完整回复（多轮上下文按 memoryId 隔离） */
    @SystemMessage(fromResource = "prompts/study-system-prompt.txt")
    String chat(@MemoryId String memoryId, @UserMessage String message);

    /** 流式对话（含资料检索工具调用）：返回 token 流（SSE 输出的 HTTP 端点由 controller 层封装） */
    @SystemMessage(fromResource = "prompts/study-system-prompt.txt")
    Flux<String> chatStream(@MemoryId String memoryId, @UserMessage String message);
}
