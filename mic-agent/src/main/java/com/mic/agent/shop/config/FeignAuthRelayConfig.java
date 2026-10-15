package com.mic.agent.shop.config;

import com.mic.agent.shop.context.AgentContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenFeign 请求头透传拦截器（对 mic-agent 全部 Feign 客户端生效）：
 * Agent 直连下游服务（不经网关），而 mic-goods / mic-user 的业务接口只信任网关鉴权后透传的
 * X-User-Id / X-User-Role 可信头，因此本拦截器在发起 Feign 调用时，
 * 从 AgentContext 取出 ChatController 已用 JwtUtil 解析校验过的用户身份，补写同样的可信头，
 * 保证工具执行的购物车 / 地址 / 订单操作都归属当前登录用户，且不信任任何客户端传入的同名头。
 */
@Configuration
public class FeignAuthRelayConfig {

    /** 与网关一致的可信用户头：当前登录用户ID */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 与网关一致的可信用户头：当前登录用户角色 */
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /**
     * 用户身份透传拦截器：已登录时透传 X-User-Id（有角色时一并透传 X-User-Role）；
     * 未登录（游客对话）时不携带用户头，由下游接口按未登录处理
     */
    @Bean
    public RequestInterceptor feignUserRelayInterceptor() {
        return template -> {
            AgentContext context = AgentContext.current();
            if (context == null || context.getUserId() == null) {
                return;
            }
            template.header(HEADER_USER_ID, String.valueOf(context.getUserId()));
            if (context.getRole() != null) {
                template.header(HEADER_USER_ROLE, String.valueOf(context.getRole()));
            }
        };
    }
}
