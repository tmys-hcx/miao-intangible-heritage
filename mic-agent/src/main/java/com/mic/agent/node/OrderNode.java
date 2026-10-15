package com.mic.agent.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mic.agent.state.MicAgentState;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 下单节点：收集订单所需信息
 * 需要信息：商品名称、购买数量、收货地址
 *
 * 运行方式（知识重点）：
 * 1、槽位抽取：用大模型从用户消息中抽取三项信息（slot filling），抽不到的字段留空；
 * 2、追问提示：把缺失的信息整理成 orderTip，由 check 校验节点判断是否齐全；
 * 3、循环边保护：check 校验不通过时会沿条件边跳回本节点（演示订单信息的"循环收集"），
 *    但单次图执行内用户并没有新的输入，若每次回来都重新追问会死循环，
 *    因此用 orderRound 记录已执行轮次：第二轮起不再重新抽取，直接把追问透传出去，把补全留给下一轮对话。
 */
@Component
public class OrderNode {

    // 注入大模型（用于从自然语言中抽取订单信息）
    private final ChatModel chatModel;

    /** 解析大模型返回的 JSON 抽取结果 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 构造注入
    public OrderNode(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public Map<String, Object> apply(MicAgentState state) {
        //获取用户当前提问
        String userQuestion = state.question();

        //从state里取出Object对象里的商品信息（state 是全局共享的，回环第二次执行能看到第一次写入的值）
        String productName = (String) state.value("productName").orElse("");
        String num = (String) state.value("buyNum").orElse("");
        String address = (String) state.value("address").orElse("");
        int round = state.orderRound();

        if (round >= 1) {
            // 回环保护：本轮用户还没有补充新信息，不再重复走大模型抽取，直接把缺失项整理成追问并结束循环
            return Map.of(
                    "productName", productName,
                    "buyNum", num,
                    "address", address,
                    "orderRound", round + 1,
                    "orderTip", buildTip(productName, num, address)
            );
        }

        // 首轮执行：把提示词发给大模型，从用户消息中抽取订单信息（只返回JSON，抽不到的字段填空字符串）
        String prompt = """
                你是一个订单信息抽取助手。请从用户消息中抽取下单所需信息：
                productName（商品名称）、buyNum（购买数量，只保留数字）、address（收货地址）。
                抽不到的字段填空字符串。只返回如下JSON，不要任何多余文字：
                {"productName":"","buyNum":"","address":""}

                用户消息：%s
                """.formatted(userQuestion);

        String json = chatModel.chat(prompt).trim();
        try {
            // 模型偶尔会包裹 ```json 等标记，先截取第一个 { 到最后一个 } 的内容再解析
            JsonNode node = MAPPER.readTree(cleanJson(json));
            productName = node.path("productName").asText("");
            num = node.path("buyNum").asText("");
            address = node.path("address").asText("");
        } catch (Exception e) {
            // 解析失败按"信息全部缺失"处理，走追问流程，保证整张图仍能正常执行完
            productName = "";
            num = "";
            address = "";
        }

        // 把抽取结果写入全局state：orderTip 告诉用户还缺什么（信息齐全则提示准备校验）
        return Map.of(
                "productName", productName,
                "buyNum", num,
                "address", address,
                "orderRound", round + 1,
                "orderTip", buildTip(productName, num, address)
        );
    }

    /** 根据缺失情况生成提示语（信息齐全时返回"收集完毕"提示，供 check 通过后透传） */
    private String buildTip(String productName, String num, String address) {
        List<String> missing = new ArrayList<>();
        if (productName.isBlank()) {
            missing.add("商品名称");
        }
        if (num.isBlank()) {
            missing.add("购买数量");
        }
        if (address.isBlank()) {
            missing.add("收货地址");
        }
        if (missing.isEmpty()) {
            return "订单信息收集完毕：商品=" + productName + "、数量=" + num + "、地址=" + address + "，准备校验订单";
        }
        return "还缺少以下信息，请补充：" + String.join("、", missing);
    }

    /** 截取字符串中第一个 { 到最后一个 } 的内容（清理模型输出中多余的说明文字或代码块标记） */
    private String cleanJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
    }
}
