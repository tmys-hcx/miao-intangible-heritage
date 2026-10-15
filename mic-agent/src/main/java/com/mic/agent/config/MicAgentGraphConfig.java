package com.mic.agent.config;

import com.mic.agent.node.AnswerNode;
import com.mic.agent.node.CheckNode;
import com.mic.agent.node.EsProductSearchNode;
import com.mic.agent.node.IntentNode;
import com.mic.agent.node.LLMThinkNode;
import com.mic.agent.node.OrderNode;
import com.mic.agent.state.MicAgentState;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 配置类：定义对话流程图
 * 作用：
 * 将上面所有 Node 注册到 LangGraph
 * 设置起始节点
 * 编写所有条件路由规则（节点之间往哪跳转）
 * 最终对外提供编译好的图 Bean（CompiledGraph，可直接 invoke 执行）
 *
 * 【langgraph4j 1.7.10 API 要点】（与网上旧版教程 demo 的写法差异，按旧写法编译不过）：
 * 1、入口节点：旧版 setEntryPoint("intent") 在 1.7.x 已移除 → 改为 addEdge(START, "intent")；
 * 2、节点包装：同步节点方法用 AsyncNodeAction.node_async(方法引用) 包装成异步动作（旧版 nodeAction 已不存在）；
 * 3、条件边：addConditionalEdges(来源节点, AsyncEdgeAction.edge_async(分支函数), mappings)，
 *    必须显式传第三个参数 mappings（"分支函数返回值 → 目标节点名"的映射表），1.7.x 没有省略 mappings 的重载；
 * 4、图的执行对象：graph.compile() 产出的 CompiledGraph（StateGraph 本身只是"图纸"，不能执行）。
 */
@Configuration
public class MicAgentGraphConfig {

    @Bean
    public CompiledGraph<MicAgentState> micAgentGraph(
            IntentNode intentNode,
            OrderNode orderNode,
            LLMThinkNode llmThinkNode,
            EsProductSearchNode esProductSearchNode,
            CheckNode checkNode,
            AnswerNode answerNode
    ) throws GraphStateException {
        // 1. 创建状态图，绑定我们自定义的MicAgentState
        StateGraph<MicAgentState> graph = new StateGraph<>(MicAgentState::new);

        // 2. 注册所有节点，把节点和名字绑定（node_async：同步方法 → 异步节点动作）
        graph.addNode("intent", AsyncNodeAction.node_async(intentNode::apply));
        graph.addNode("order", AsyncNodeAction.node_async(orderNode::apply));
        graph.addNode("llmThink", AsyncNodeAction.node_async(llmThinkNode::apply));
        graph.addNode("esSearch", AsyncNodeAction.node_async(esProductSearchNode::apply));
        graph.addNode("check", AsyncNodeAction.node_async(checkNode::apply));
        graph.addNode("answer", AsyncNodeAction.node_async(answerNode::apply));

        // 3. 设置入口节点：START → intent（1.7.x 用 START 常量 + 普通边替代 setEntryPoint）
        graph.addEdge(START, "intent");

        // ========== 路由1：intent节点的条件分支 ==========
        // 分支函数返回"分支键"，由 mappings 映射到真正的目标节点
        graph.addConditionalEdges("intent",
                AsyncEdgeAction.edge_async(state -> switch (state.intent()) {
                    case "order" -> "order";                 // 下单业务 → 进入订单信息收集
                    case "product_query" -> "llmThink";      // 商品查询 → 先思考是否需要检索
                    case "chat" -> "answer";                 // 闲聊 → 直接生成回复
                    default -> "answer";                     // 未知意图兜底：生成回复后结束
                }),
                Map.of(
                        "order", "order",
                        "llmThink", "llmThink",
                        "answer", "answer"
                ));

        // ========== 路由2：order节点执行完，直接进入check校验节点 ==========
        graph.addEdge("order", "check");

        // ========== 路由3：llmThink节点，根据needEsSearch判断 ==========
        graph.addConditionalEdges("llmThink",
                AsyncEdgeAction.edge_async(state ->
                        state.needEsSearch() ? "esSearch" : "answer"),
                Map.of(
                        "esSearch", "esSearch",
                        "answer", "answer"
                ));

        // ========== 路由4：esSearch执行完，进入check校验节点 ==========
        graph.addEdge("esSearch", "check");

        // ========== 路由5：check校验节点的条件分支 ==========
        graph.addConditionalEdges("check",
                AsyncEdgeAction.edge_async(state -> {
                    MicAgentState.CheckResult res = state.checkResult();
                    if (res != null && res.isPass()) {
                        // 校验通过 → 生成最终回复
                        return "answer";
                    }
                    if (res != null && "info_missing".equals(res.getErrorType()) && state.orderRound() <= 1) {
                        // 订单信息缺失且还未重试过 → 回到order节点继续收集（循环边，最多回环1次）
                        return "order";
                    }
                    // 其余情况（deny / 回环后信息仍缺失）→ 统一走answer生成最终回复（拒绝说明或追问）
                    return "answer";
                }),
                Map.of(
                        "answer", "answer",
                        "order", "order"
                ));

        // ========== 路由6：answer（终端节点）→ END 结束整张图 ==========
        graph.addEdge("answer", END);

        // 4. 编译图：StateGraph 只是图纸，compile() 产出的 CompiledGraph 才是可执行对象
        return graph.compile();
    }
}
