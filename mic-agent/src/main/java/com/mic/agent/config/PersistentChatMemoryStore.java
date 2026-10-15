package com.mic.agent.config;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 🟢【新增】配置聊天的持久化记忆（自参考实现补移植）：
 * 原项目 ChatMemoryConfig 引用了本类但类缺失导致编译失败，此处按参考实现恢复。
 * 使用redis存储聊天记录，
 * 每个 memoryId 对应一个 Redis key，消息列表以 JSON 形式存储
 */
@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {

    /**
     * Redis key 前缀，完整 key 形如 chat:memory:{memoryId}
     */
    private static final String KEY_PREFIX = "chat:memory:";

    /** 工具调用缺响应时的占位结果文本：读取修复补位用；同 id 重复收敛时占位永远让位于真实结果 */
    private static final String MISSING_RESULT_TEXT = "该工具调用未返回结果（执行失败或中断），请勿引用其数据。";

    //redis连接操作对象
    private final StringRedisTemplate redisTemplate;

    public PersistentChatMemoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redisTemplate.opsForValue().get(key(memoryId));
        if (json == null) {
            // 首次会话无历史记录时返回空列表
            return List.of();
        }
        // 读取时收敛工具调用历史：补齐缺失响应、去重并保证占位不吞真实结果；
        // 不收敛则中断历史会让该会话后续所有请求被 DeepSeek 以 400 拒绝
        return repairUnmatchedToolCalls(ChatMessageDeserializer.messagesFromJson(json));
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        redisTemplate.opsForValue().set(key(memoryId), ChatMessageSerializer.messagesToJson(messages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(key(memoryId));
    }

    /**
     * 重建工具调用历史的协议一致性（OpenAI 兼容协议硬性约束，DeepSeek 严格校验，违规即 400）：
     * 每个 assistant(tool_calls) 中的 tool_call_id 必须紧跟且仅有一条对应的 tool 响应消息。
     * 工具执行中断 / 执行期间中间态读取 / 并发写入会让历史出现四类非法形态，本方法一次收敛（幂等，可反复执行）：
     * 1、缺失响应：为未获得响应的 tool_call 补一条占位响应（正文约束模型不引用失败调用数据）；
     * 2、重复响应：同一 tool_call_id 出现多条 tool 消息时收敛为一条，真实结果优先于占位；
     * 3、孤儿响应：前面没有对应 tool_calls 的 tool 消息直接丢弃；响应 id 丢失时按 tool_calls 顺序配对并重建补回 id；
     * 4、占位让位于真实：LangChain4j 的 ChatMemoryStore 每次 add 均“先读后写”，
     *    工具执行期间 assistant(tool_calls) 已入库而真实结果尚未写入，期间任何读取都会先补占位，
     *    真实结果随后写入形成“占位 + 真实结果”双响应；收敛时真实结果必须替换占位，绝不能让占位吞掉真实数据。
     */
    private List<ChatMessage> repairUnmatchedToolCalls(List<ChatMessage> messages) {
        List<ChatMessage> repaired = new ArrayList<>(messages.size());
        // 当前工具调用轮次待响应的 tool_call（id → 工具名，LinkedHashMap 保持补位顺序稳定）
        Map<String, String> pendingCalls = new LinkedHashMap<>();
        // 当前轮次已保留响应：id → 响应在 repaired 中的下标（同一 id 只保留一条）
        Map<String, Integer> respondedIndexes = new HashMap<>();
        // 已保留响应为“占位文本”的 id：真实结果后到时用于替换占位
        Set<String> placeholderIds = new HashSet<>();
        for (ChatMessage message : messages) {
            if (message instanceof ToolExecutionResultMessage toolMessage) {
                boolean placeholder = MISSING_RESULT_TEXT.equals(toolMessage.text());
                String id = toolMessage.id();
                // id 优先精确匹配；id 丢失（反序列化未还原）时按 tool_calls 声明顺序配对补位，并重建补回 id
                String matchedId = (id != null && pendingCalls.containsKey(id)) ? id : nextUnrespondedId(pendingCalls, respondedIndexes);
                if (matchedId == null) {
                    continue; // 孤儿响应（当前轮次无待响应调用）：直接丢弃
                }
                ToolExecutionResultMessage normalized = id != null && id.equals(matchedId)
                        ? toolMessage
                        : ToolExecutionResultMessage.from(matchedId, pendingCalls.get(matchedId), toolMessage.text());
                Integer existingIndex = respondedIndexes.get(matchedId);
                if (existingIndex == null) {
                    respondedIndexes.put(matchedId, repaired.size());
                    if (placeholder) {
                        placeholderIds.add(matchedId);
                    }
                    repaired.add(normalized);
                } else if (placeholderIds.contains(matchedId) && !placeholder) {
                    // 已保留的是占位、新到的是真实结果：真实结果替换占位（绝不让占位吞掉真实数据）
                    repaired.set(existingIndex, normalized);
                    placeholderIds.remove(matchedId);
                }
                // 其余情况（真实已保留 / 重复占位）：丢弃后到的重复响应
                continue;
            }
            // 遇到非 tool 消息：先关闭当前轮次（补全缺失响应），再收录本条消息
            closeToolCallRound(repaired, pendingCalls, respondedIndexes, placeholderIds);
            repaired.add(message);
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    if (request.id() != null) {
                        pendingCalls.put(request.id(), request.name());
                    }
                }
            }
        }
        // 收尾：历史末尾仍处于打开状态的轮次同样补全
        closeToolCallRound(repaired, pendingCalls, respondedIndexes, placeholderIds);
        return repaired;
    }

    /** 当前轮次第一个尚未获得响应的 tool_call id（按 tool_calls 声明顺序），全部已响应时返回 null */
    private String nextUnrespondedId(Map<String, String> pendingCalls, Map<String, Integer> respondedIndexes) {
        for (String id : pendingCalls.keySet()) {
            if (!respondedIndexes.containsKey(id)) {
                return id;
            }
        }
        return null;
    }

    /** 关闭当前工具调用轮次：为缺失响应的 tool_call 补占位消息后清空轮次状态 */
    private void closeToolCallRound(List<ChatMessage> repaired, Map<String, String> pendingCalls,
                                    Map<String, Integer> respondedIndexes, Set<String> placeholderIds) {
        if (pendingCalls.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> pending : pendingCalls.entrySet()) {
            if (!respondedIndexes.containsKey(pending.getKey())) {
                repaired.add(ToolExecutionResultMessage.from(pending.getKey(), pending.getValue(), MISSING_RESULT_TEXT));
            }
        }
        pendingCalls.clear();
        respondedIndexes.clear();
        placeholderIds.clear();
    }

    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}
