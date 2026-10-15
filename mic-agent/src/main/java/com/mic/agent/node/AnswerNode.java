package com.mic.agent.node;

import com.mic.agent.state.MicAgentState;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 回复生成节点（终端节点）：把全流程的产物整理成最终回复写入 state 的 answer 字段
 *
 * 为什么要补这个节点：原 demo 流程的终点只有 check 的校验结果，没有任何"生成用户可见文本"的环节，
 * chat / 商品查询路径执行完用户看不到回答，因此新增 answer 节点统一生成最终回复，
 * 并在配置类中作为所有终态的收口（校验通过、拒绝、闲聊都汇入这里再结束整张图）。
 */
@Component
public class AnswerNode {

    private final ChatModel chatModel;

    // 构造注入
    public AnswerNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Map<String, Object> apply(MicAgentState state) {
        String question = state.question();
        String intent = state.intent();
        String orderTip = state.orderTip();
        List<String> productDocs = state.productDocs();

        // 订单场景：直接把 order 节点生成的提示/追问透传给用户（不经过模型改写，避免被误述成"订单已创建"）
        if ("order".equals(intent)) {
            return Map.of("answer", orderTip.isBlank() ? "订单信息处理中，请稍后补充相关信息" : orderTip);
        }

        // 其余场景：把上游节点产出的上下文交给大模型，由模型组织自然语言回复
        String context;
        if ("product_query".equals(intent)) {
            context = productDocs.isEmpty()
                    ? "商品检索结果：未检索到相关商品"
                    : "商品检索结果：\n" + String.join("\n", productDocs);
        } else {
            context = "无额外上下文（普通闲聊，直接回答用户问题即可）";
        }

        String prompt = """
                你是非遗商城智能助手"阿银"。请结合上下文信息，用简洁友好的中文回答用户问题。
                规则：只能依据上下文中的商品信息作答，不要编造库存、价格等数据；
                若上下文提示未检索到商品，请如实说明并建议换个关键词。

                上下文信息：%s
                用户的问题：%s
                """.formatted(context, question);

        String answer = chatModel.chat(prompt).trim();
        // 写入全局state：answer 字段即编排流程对外返回的最终答案
        return Map.of("answer", answer);
    }
}
