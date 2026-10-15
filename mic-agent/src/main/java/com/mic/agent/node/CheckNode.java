package com.mic.agent.node;

import com.mic.agent.state.MicAgentState;
import com.mic.agent.state.MicAgentState.CheckResult;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/**
 * 校验节点：校验订单信息 或者 商品查询结果
 * 校验结果写入 state 的 checkResult 字段，由配置类（MicAgentGraphConfig 路由5）决定后续跳转：
 * 通过 → answer 生成回复；订单信息缺失且未重试过 → 跳回 order 继续收集；其余 → answer 收尾
 */
@Component
public class CheckNode {

    private final ChatModel chatModel;

    public CheckNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Map<String, Object> apply(MicAgentState state) {
        String intent = state.intent();
        List<String> productDocs = state.productDocs();
        String productName = (String) state.value("productName").orElse("");
        String buyNum = (String) state.value("buyNum").orElse("");
        String address = (String) state.value("address").orElse("");

        CheckResult checkResult;

        if ("order".equals(intent)) {
            // 下单场景校验：检查订单信息是否齐全
            if (productName.isBlank() || buyNum.isBlank() || address.isBlank()) {
                // 订单信息不全，需要回去继续收集
                checkResult = new CheckResult(false, "info_missing");
            } else {
                // 订单信息全部齐全，校验通过
                checkResult = new CheckResult(true, "pass");
            }
        } else if ("product_query".equals(intent)) {
            // 商品查询场景校验
            if (state.esEmpty()) {
                // ES没有查到商品，拒绝
                checkResult = new CheckResult(false, "deny");
            } else {
                // 查询到商品，校验通过
                checkResult = new CheckResult(true, "pass");
            }
        } else {
            // 其他意图默认拒绝
            checkResult = new CheckResult(false, "deny");
        }

        // 将校验结果存入全局state
        return Map.of("checkResult", checkResult);
    }
}