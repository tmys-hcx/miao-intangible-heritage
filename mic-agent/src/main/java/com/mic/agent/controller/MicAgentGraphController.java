package com.mic.agent.controller;

import com.mic.agent.model.dto.AgentChatDTO;
import com.mic.agent.model.vo.MicAgentGraphReplyVO;
import com.mic.agent.state.MicAgentState;
import com.mic.common.exception.BusinessException;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * LangGraph 智能体编排入口（实验接口）：
 * 把用户消息作为初始状态交给 MicAgentGraphConfig 编译好的图执行，返回路由意图与最终回复。
 * 与 /api/agent/chat（AiService 工具对话）互不影响，用于单独验证编排链路：
 * START → 意图识别 → 条件路由 →（订单收集循环 / 商品思考与检索 / 闲聊）→ 校验 → 回复生成 → END
 */
@Tag(name = "LangGraph智能体编排接口")
@RestController
@RequestMapping("/api/agent/graph")
@RequiredArgsConstructor
public class MicAgentGraphController {

    /** 配置类（MicAgentGraphConfig）中编译好的可执行图 Bean */
    private final CompiledGraph<MicAgentState> micAgentGraph;

    @Operation(summary = "编排对话（同步）：执行完整状态图并返回最终回复（intent 字段便于观察路由结果）")
    @PostMapping("/chat")
    public Result<MicAgentGraphReplyVO> chat(@Valid @RequestBody AgentChatDTO dto) {
        // 构造图的初始状态：把用户提问写入 state 的 question 字段（所有节点都从 state 读取数据）
        Map<String, Object> initState = new HashMap<>();
        initState.put("question", dto.getMessage());

        MicAgentState finalState;
        try {
            // invoke：从入口节点开始执行整张图，直到 END；返回 Optional<最终state>
            finalState = micAgentGraph.invoke(initState)
                    .orElseThrow(() -> new BusinessException(ResultCodeEnum.ERROR, "编排流程未产出最终状态"));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 图执行异常（模型调用失败 / 递归超限等）：转为业务异常，交由全局异常处理器统一返回
            throw new BusinessException(ResultCodeEnum.ERROR, "智能体编排执行失败：" + e.getMessage());
        }

        return Result.ok(new MicAgentGraphReplyVO(finalState.intent(), finalState.answer()));
    }
}
