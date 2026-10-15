package com.mic.agent.controller;

import com.mic.agent.assistant.ChatAssistant;
import com.mic.agent.model.dto.AgentChatDTO;
import com.mic.agent.model.vo.ChatReplyVO;
import com.mic.agent.shop.context.AgentContext;
import com.mic.common.result.Result;
import com.mic.common.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * 智能助手对话接口（双主题共用：传承与学习 / 非遗商品，系统提示词按用户意图路由）：
 * 1、会话记忆：登录用户以 JWT token 作为会话ID（不同用户会话天然隔离，Redis 持久化多轮上下文）；
 *    游客以 guest:sessionId 隔离（sessionId 由前端生成、缺省服务端随机生成按单轮处理）；
 * 2、用户身份：从 Authorization 头解析（JwtUtil 与网关同一密钥），绑定 AgentContext 后，
 *    购物 / 订单 @Tool 工具校验登录态、Feign 拦截器透传 X‑User‑Id / X‑User‑Role 可信头调用 mic‑goods / mic‑user，
 *    保证购物车 / 地址 / 订单只查询与操作当前登录用户本人的数据（含订单查询 / 取消 / 申请退款）；
 * 3、下单联动：购物工具创建订单后订单号写入 AgentContext，随响应 payOrderNo 字段返回，
 *    前端据此弹出扫码支付引导并跳转模拟支付页；
 * 4、登录态说明：网关鉴权白名单放行本接口（游客可用），未携带或已失效 token 按游客处理
 *    （涉及购物车 / 地址 / 订单等需要登录身份的工具会返回"请先登录"提示）；
 * 5、消息组装（每次请求强制执行）：系统提示词取 agent‑prompt.txt 完整全文（AssistantConfig 的
 *    systemMessageProvider 每请求回调加载，不截断、不删减），固定置于消息数组第一位；其后为
 *    Redis 恢复的历史对话消息，最后为当前用户消息（头部动态追加强制指令前缀，见 COMPLIANCE_INSTRUCTION）。
 * 6、theme字段：仅做业务标记、日志打印，实际AI意图由系统提示词自行路由。
 * 7、study子接口（学习资料推荐 / 论坛草稿 / 论坛总结）：已由 StudyAssistController（/api/agent/study/**）
 *    独立实现（真实 Feign 检索 + 模型生成），本控制器不再重复映射，避免路由冲突。
 */
@Tag(name = "智能助手对话接口")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class ChatController {

    /** Authorization 头前缀（与网关解析规则一致） */
    private static final String TOKEN_PREFIX = "Bearer ";

    /** 游客会话记忆ID前缀（与登录用户的 token 会话ID区分） */
    private static final String GUEST_MEMORY_PREFIX = "guest:";

    /** 强制指令前缀：每次调用大模型时动态追加在用户消息头部（要求模型优先严格遵守系统提示词全部约束） */
    private static final String COMPLIANCE_INSTRUCTION =
            "【请你优先阅读并严格遵守系统提示词内全部约束规则之后，再回答用户的问题，输出必须完全服从系统提示中的输出格式、业务流程、禁止项，不要自作主张改变输出排版格式】";

    private final ChatAssistant chatAssistant;

    private final JwtUtil jwtUtil;

    @Operation(summary = "智能助手对话（同步）：请求头 Authorization 携带 JWT（可空，游客可用），返回 AI 回复与下单订单号（订单查询 / 取消 / 申请退款均在对话内完成）")
    @PostMapping("/chat")
    public Result<ChatReplyVO> chat(@Valid @RequestBody AgentChatDTO dto,
                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        // 解析 Authorization 头：token 有效则认定为登录用户；未携带或已失效按游客处理
        String token = resolveToken(authorization);
        Long userId = null;
        Integer role = null;
        if (token != null && jwtUtil.validateToken(token)) {
            userId = jwtUtil.getUserId(token);
            role = jwtUtil.getRole(token);
        }
        // 会话记忆ID：登录用户以 JWT token 作为会话ID（Redis key = chat:memory:{token}，
        // 不同登录用户天然隔离；游客以 guest:sessionId 隔离）
        String memoryId = userId != null
                ? token
                : GUEST_MEMORY_PREFIX + resolveSessionId(dto.getSessionId());
        // 绑定请求上下文：@Tool 工具与 Feign 拦截器据此识别用户身份、透传可信头
        AgentContext.bind(userId, role, token);
        try {
            // 用户消息头部追加强制指令（每次请求强制执行，确保模型严格遵守系统提示词的约束与输出格式）
            String reply = chatAssistant.chat(memoryId, composeUserMessage(dto.getMessage()));
            AgentContext context = AgentContext.current();
            // payOrderNo 非空表示本轮对话创建了待付款订单，前端弹出扫码支付引导
            return Result.ok(new ChatReplyVO(reply, context == null ? null : context.getPayOrderNo()));
        } finally {
            // 请求结束清理上下文（含订单号），防止线程复用导致身份串号
            AgentContext.clear();
        }
    }

    @Operation(summary = "智能助手对话（流式 SSE）：逐 token 返回，会话记忆规则同同步接口；购物下单、订单操作等工具流程请使用同步接口")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody AgentChatDTO dto,
                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        // 流式输出为异步线程执行，工具运行时无法读取 ThreadLocal 上下文（用户身份 / 订单号回传），
        // 故流式端点仅承载问答（商品查询类工具仍可用；购物车 / 地址 / 订单等需要登录身份的工具会提示未登录），
        // 下单与订单操作流程请使用同步接口
        String token = resolveToken(authorization);
        Long userId = token != null && jwtUtil.validateToken(token) ? jwtUtil.getUserId(token) : null;
        String memoryId = userId != null
                ? token
                : GUEST_MEMORY_PREFIX + resolveSessionId(dto.getSessionId());
        return chatAssistant.chatStream(memoryId, composeUserMessage(dto.getMessage()));
    }

    /** 组装用户侧消息：头部追加强制指令前缀（同步与流式端点统一执行） */
    private String composeUserMessage(String message) {
        return COMPLIANCE_INSTRUCTION + "\n" + message;
    }

    /** 提取 Bearer token（与网关同一规则；无 token 或空串返回 null） */
    private String resolveToken(String authorization) {
        if (authorization == null || !authorization.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        String token = authorization.substring(TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** 会话ID缺省时生成随机值（按单轮无上下文处理），前端正常会携带以便多轮对话 */
    private String resolveSessionId(String sessionId) {
        return StringUtils.hasText(sessionId) ? sessionId : UUID.randomUUID().toString();
    }
}