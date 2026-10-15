package com.mic.agent.node;

import com.mic.agent.state.MicAgentState;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 意图识别节点：电商场景
 * 识别用户输入是闲聊 / 下单 / 查询商品
 */
@Component
public class IntentNode {

    // 注入大模型
    private final ChatModel chatModel;

    // 构造注入
    public IntentNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    //节点入口方法
    public Map<String, Object> apply(MicAgentState state) {
        String userQuestion = state.question();

        String prompt = """
                判断用户提问意图，**只返回下面其中一个单词，不要多余文字**
                可选值：
                chat（闲聊对话）
                order（下单业务，用户想要提交订单）
                product_query（商品查询，询问商品信息）

                用户问题：%s
                """.formatted(userQuestion);

        // =========重点修改：generate 改为 chat =========
        //把提示词发给大模型，拿到返回的意图
        String raw = chatModel.chat(prompt).trim().toLowerCase();
        // 兜底归一化：模型偶尔会带多余文字（如"意图：chat"），按关键词命中判断，保证条件路由稳定
        String intent = raw.contains("order") ? "order"
                : raw.contains("product_query") ? "product_query"
                : "chat";
        return Map.of("intent", intent);
    }
}