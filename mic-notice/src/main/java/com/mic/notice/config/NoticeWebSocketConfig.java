package com.mic.notice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置（通知实时推送）
 * 注册通知推送处理器 NoticeWebSocketHandler，路径 /api/notice/ws
 * （与控制器同前缀，经网关 /api/notice/** 路由转发；握手为普通 GET 请求，
 * 浏览器 WebSocket API 无法携带 Authorization 头，故网关白名单放行该路径，
 * 用户身份由查询参数 token 在握手时校验）
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class NoticeWebSocketConfig implements WebSocketConfigurer {

    /** 通知推送处理器：维护用户在线会话并向其推送新通知 */
    private final NoticeWebSocketHandler noticeWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(noticeWebSocketHandler, "/api/notice/ws")
                // 本地演示：允许任意来源（前端经 Vite 代理 / 网关跨端口访问）
                .setAllowedOrigins("*");
    }
}
