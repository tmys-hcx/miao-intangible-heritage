package com.mic.goods.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mic.common.util.DateUtil;
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
 * 模拟支付 WebSocket 推送处理器
 * 电脑端模拟支付页建立长连接订阅订单（/api/goods/order/ws/pay?orderNo=xxx，会话按订单号分组管理）；
 * 手机扫码确认支付成功 / 订单超时自动取消时，向该订单的全部订阅会话推送 JSON 消息：
 * PAID 支付成功（订单进入 1待发货）；TIMEOUT 支付超时（订单已自动取消并归还库存）
 */
@Slf4j
@Component
public class PayWebSocketHandler extends TextWebSocketHandler {

    /** 会话属性键：订阅的订单号（连接关闭时用于定位订阅分组） */
    private static final String ATTR_ORDER_NO = "payOrderNo";

    /** 订单号 → 订阅会话集合（同一订单支持多端订阅；连接关闭 / 推送失败时移除） */
    private final Map<String, Set<WebSocketSession>> orderSessions = new ConcurrentHashMap<>();

    /** 建立连接：解析查询参数 orderNo 完成订阅；缺失订单号直接关闭连接 */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String orderNo = resolveOrderNo(session);
        if (orderNo == null) {
            session.close(CloseStatus.BAD_DATA.withReason("missing orderNo"));
            return;
        }
        session.getAttributes().put(ATTR_ORDER_NO, orderNo);
        orderSessions.computeIfAbsent(orderNo, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("模拟支付 WebSocket 订阅成功：orderNo={}, sessionId={}", orderNo, session.getId());
    }

    /** 连接关闭：从对应订单的订阅集合中移除会话（集合为空时清理映射） */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object orderNo = session.getAttributes().get(ATTR_ORDER_NO);
        if (orderNo != null) {
            Set<WebSocketSession> sessions = orderSessions.get(orderNo.toString());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    orderSessions.remove(orderNo.toString());
                }
            }
        }
        log.info("模拟支付 WebSocket 断开：sessionId={}, status={}", session.getId(), status);
    }

    /** 推送支付成功消息（手机端模拟支付成功后调用） */
    public void pushPaid(String orderNo) {
        push(orderNo, "PAID", "支付成功，订单已进入待发货状态");
    }

    /** 推送支付超时通知（定时任务自动取消超时订单后调用） */
    public void pushTimeout(String orderNo) {
        push(orderNo, "TIMEOUT", "支付超时，订单已自动取消并归还库存");
    }

    /** 向订阅该订单的全部会话推送 JSON 消息：无订阅者时静默忽略；单会话失败仅告警并移除，不影响其余会话 */
    private void push(String orderNo, String type, String message) {
        Set<WebSocketSession> sessions = orderSessions.get(orderNo);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        JSONObject payload = JSONUtil.createObj()
                .set("type", type)
                .set("orderNo", orderNo)
                .set("message", message)
                .set("time", DateUtil.now());
        String text = payload.toString();
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
                log.info("模拟支付 WebSocket 推送成功：orderNo={}, type={}, sessionId={}", orderNo, type, session.getId());
            } catch (Exception e) {
                sessions.remove(session);
                log.warn("模拟支付 WebSocket 推送失败（移除该订阅）：orderNo={}, sessionId={}, err={}",
                        orderNo, session.getId(), e.getMessage());
            }
        }
    }

    /** 解析订阅的订单号（查询参数 orderNo，缺失返回 null） */
    private String resolveOrderNo(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String orderNo = UriComponentsBuilder.fromUri(session.getUri()).build()
                .getQueryParams().getFirst("orderNo");
        return orderNo == null || orderNo.isBlank() ? null : orderNo;
    }
}
