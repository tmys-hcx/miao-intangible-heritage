package com.mic.goods.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置（沙箱模拟支付）
 * 注册模拟支付推送处理器 PayWebSocketHandler，路径 /api/goods/order/ws/pay
 * （与控制器同前缀，经网关 /api/goods/** 路由转发；握手为普通 GET 请求，
 * 浏览器 WebSocket API 无法携带 Authorization 头，故网关白名单放行该路径）；
 * 电脑端模拟支付页连接时通过查询参数 orderNo 指定订阅的订单号
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    /** 模拟支付推送处理器：维护订单订阅会话并向电脑端推送支付结果 */
    private final PayWebSocketHandler payWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(payWebSocketHandler, "/api/goods/order/ws/pay")
                // 沙箱演示：允许任意来源（前端经 Vite 代理 / 网关跨端口访问）
                .setAllowedOrigins("*");
    }
}
