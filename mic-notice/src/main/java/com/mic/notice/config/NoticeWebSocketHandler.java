package com.mic.notice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mic.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知实时推送 WebSocket 处理器
 * 登录用户前端（顶栏铃铛）建立长连接订阅自己的通知（/api/notice/ws?token=xxx，会话按用户ID分组管理）；
 * 通知落库成功后（订单状态变更经 RabbitMQ 消费 / 评论回复经 Feign 调用，统一走 NotificationService.create），
 * 向该用户的全部在线会话实时推送 NoticeVO JSON（经全局 Jackson 序列化，格式与 /api/notice/list 完全一致），
 * 前端据此即时更新未读角标与列表，无需轮询刷新
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeWebSocketHandler extends TextWebSocketHandler {

    /** 会话属性键：连接对应的用户ID（连接关闭时用于定位用户分组） */
    private static final String ATTR_USER_ID = "noticeUserId";

    /** 用户ID → 在线会话集合（同一用户支持多标签页；连接关闭 / 推送失败时移除） */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    /** JWT 工具：握手时校验查询参数 token（浏览器 WebSocket API 无法携带 Authorization 头，故经 token 参数鉴权） */
    private final JwtUtil jwtUtil;

    /** JSON 序列化：沿用全局 Jackson 配置（LocalDateTime 输出 yyyy-MM-dd HH:mm:ss，与列表接口输出一致） */
    private final ObjectMapper objectMapper;

    /** 建立连接：解析查询参数 token 完成鉴权并按用户ID注册会话；缺失 / 无效 token 直接关闭连接 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = resolveToken(session);
        if (token == null || !jwtUtil.validateToken(token)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid token"));
            return;
        }
        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid token"));
            return;
        }
        session.getAttributes().put(ATTR_USER_ID, userId);
        userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("通知 WebSocket 连接成功：userId={}, sessionId={}", userId, session.getId());
    }

    /** 连接关闭：从对应用户的会话集合中移除会话（集合为空时清理映射） */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get(ATTR_USER_ID);
        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        log.info("通知 WebSocket 断开：sessionId={}, status={}", session.getId(), status);
    }

    /**
     * 向指定用户的全部在线会话推送通知（通知创建成功后由业务侧调用）：
     * 对象经全局 Jackson 序列化；无在线会话时静默忽略；单会话失败仅告警并移除，不影响其余会话
     */
    public void push(Long userId, Object payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String text;
        try {
            text = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("通知消息序列化失败，跳过推送：userId={}, err={}", userId, e.getMessage());
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                // WebSocketSession 并发发送不安全：同一会话串行发送
                synchronized (session) {
                    session.sendMessage(new TextMessage(text));
                }
                log.info("通知实时推送成功：userId={}, sessionId={}", userId, session.getId());
            } catch (Exception e) {
                sessions.remove(session);
                log.warn("通知实时推送失败（移除该会话）：userId={}, sessionId={}, err={}",
                        userId, session.getId(), e.getMessage());
            }
        }
    }

    /** 解析握手查询参数中的 token（缺失返回 null） */
    private String resolveToken(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String token = UriComponentsBuilder.fromUri(session.getUri()).build()
                .getQueryParams().getFirst("token");
        return token == null || token.isBlank() ? null : token;
    }
}
