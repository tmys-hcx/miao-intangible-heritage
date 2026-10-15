package com.mic.agent.state;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;

/**
 * 自定义Agent状态类,存用户问题和各个节点数据
 *
 * 知识点：LangGraph4j 的 state 本质是一个 Map&lt;String,Object&gt;（所有节点共享的"全局黑板"），
 * 每个节点执行完返回的 Map 会合并进 state，后续节点通过下面的取值方法读取上游节点的产物；
 * 条件路由函数（MicAgentGraphConfig 里）同样读取 state 来决定跳转方向。
 *
 * 注意：langgraph4j 1.7.x 的 value(key) 返回 Optional&lt;Object&gt;（旧版教程里是泛型 Optional&lt;T&gt;，
 * 可直接赋值给 String），因此统一取值后按业务类型强转。
 */
public class MicAgentState extends AgentState {

    //Map<String,Object>用来存放流程里所有节点共享的数据
    public MicAgentState(Map<String, Object> data) {
        super(data);
    }

    // ========== 根据key在map中取值（1.7.x 需强转为业务类型） ==========

    // 用户原始提问
    public String question() {
        return (String) value("question").orElse("");
    }

    // 用户意图 chat / order / product_query
    public String intent() {
        return (String) value("intent").orElse("");
    }

    // 是否需要检索商品ES
    public Boolean needEsSearch() {
        return (Boolean) value("needEsSearch").orElse(false);
    }

    // ES查询结果是否为空
    public Boolean esEmpty() {
        return (Boolean) value("esEmpty").orElse(true);
    }

    // ES返回商品文档列表
    @SuppressWarnings("unchecked")
    public List<String> productDocs() {
        return (List<String>) value("productDocs").orElse(List.of());
    }

    // 订单校验结果
    public CheckResult checkResult() {
        return (CheckResult) value("checkResult").orElse(null);
    }

    // 订单信息收集提示语（order节点生成："还缺什么信息"的追问 / "收集完毕"提示）
    public String orderTip() {
        return (String) value("orderTip").orElse("");
    }

    // order节点已执行轮次（用于防止 check ↔ order 条件边无限循环）
    public Integer orderRound() {
        return (Integer) value("orderRound").orElse(0);
    }

    // 最终回复文本（answer节点生成，作为编排流程对外返回的答案）
    public String answer() {
        return (String) value("answer").orElse("");
    }

    /**
     * 订单校验内部实体：下单信息校验结果
     * pass=true 表示校验通过；errorType 取值：pass（通过）/ info_missing（信息缺失）/ deny（拒绝）
     */
    public static class CheckResult {
        private boolean pass;
        private String errorType;

        public CheckResult(boolean pass, String errorType) {
            this.pass = pass;
            this.errorType = errorType;
        }

        public boolean isPass() {
            return pass;
        }

        public String getErrorType() {
            return errorType;
        }
    }
}
