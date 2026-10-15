package com.mic.agent.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * LangGraph 智能体编排回复VO（/api/agent/graph/chat）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MicAgentGraphReplyVO implements Serializable {

    /** 意图识别结果：chat 闲聊 / order 下单 / product_query 商品查询（便于观察条件路由走向） */
    private String intent;

    /** answer 节点生成的最终回复文本 */
    private String answer;
}
