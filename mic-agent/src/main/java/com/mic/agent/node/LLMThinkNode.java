package com.mic.agent.node;
import com.mic.agent.state.MicAgentState;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 商品查询思考节点
 * 判断回答用户商品问题，是否需要检索ES商品库
 */
@Component
public class LLMThinkNode {
    private final ChatModel chatModel;

    public LLMThinkNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Map<String, Object> apply(MicAgentState state) {
        // 获取用户提问
        String userQuestion = state.question();

        String prompt = """
                判断回答用户这个商品问题，是否需要查询商品库存/商品详情。
                只允许返回单词 true 或者 false，不要多余文字。
                true：需要查询ES商品库
                false：不需要，可以直接回答

                用户问题：%s
                """.formatted(userQuestion);

        String resultStr = chatModel.chat(prompt).trim().toLowerCase();
        // 兜底解析：模型偶尔会返回多余文字（如"true。"），改按是否包含 true 判断；解析不出时默认 false（不检索）
        boolean needEs = resultStr.contains("true");

        // 将判断结果存入全局state
        return Map.of("needEsSearch", needEs);
    }

}
