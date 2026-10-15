package com.mic.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.common.util.JwtUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 全局token校验过滤器
 * - 白名单（验证码、注册、登录、轮播、资讯、商品分类/商品/评价、沙箱模拟支付手机端确认接口与 WebSocket 订阅、非遗资料公开分页、非遗评论分页、非遗传承公开分页、论坛帖子分页、话题列表与回复分页、智能助手对话 / LangGraph编排对话 / 学习内容推荐 / 论坛智能总结）与跨域预检请求直接放行
 *
 * - 免登录 GET 动态路径（商品 / 非遗资料 / 非遗传承 / 论坛帖子详情）按可选鉴权处理：
 *   未携带或已失效 token 按游客放行（仅可见公开数据）；有效 token 则剥离伪造头后透传用户信息，供后端识别本人（如本人预览未发布资料）
 * - 其余接口校验 Authorization: Bearer token，token失效返回 code=401
 * - 校验通过后剥离客户端伪造头，向下游透传可信用户信息（X-User-Id / X-User-Role）
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 无需登录即可访问的接口（网关完整路径，精确匹配） */
    private static final List<String> WHITE_LIST = List.of(
            "/api/user/captcha",
            "/api/user/register",
            "/api/user/login",
            "/api/user/banner/page",
            "/api/user/news/page",
            "/api/goods/category/list",
            "/api/goods/category/page",
            "/api/goods/page",
            "/api/goods/latest",
            "/api/goods/evaluate/page",
            // 沙箱模拟支付（手机端扫码确认页）：凭二维码携带的一次性支付令牌免登录访问
            "/api/goods/order/pay/info",
            "/api/goods/order/pay/mock",
            // 沙箱模拟支付 WebSocket 订阅（电脑端模拟支付页）：浏览器 WebSocket API 无法携带 Authorization 头
            "/api/goods/order/ws/pay",
            "/api/culture/page",
            "/api/culture/comment/page",
            "/api/inherit/page",
            // 论坛板块公开接口：帖子分页 + 话题列表 + 回复分页（发布 / 编辑 / 删除 / 发表回复仍强制鉴权）
            "/api/inherit/forum/page",
            "/api/inherit/forum/topic/list",
            "/api/inherit/forum/reply/page",
            // 智能助手对话（游客可用：资料检索问答不依赖用户身份）
            "/api/agent/chat",
            // LangGraph 智能体编排对话（实验接口：演示意图路由与流程编排，不依赖用户身份）
            "/api/agent/graph/chat",
            // 学习内容推荐（详情页配套推荐：与公开详情一致，游客可用）
            "/api/agent/study/recommend",
            // 论坛智能总结（论坛搜索框下方智能总结：与论坛公开搜索一致，游客可用）
            "/api/agent/study/forum/summary",
            // 通知实时推送 WebSocket（顶栏铃铛）：浏览器 WebSocket API 无法携带 Authorization 头，token 经查询参数传递，由 mic-notice 握手时校验
            "/api/notice/ws");

    /** 免登录 GET 动态路径（可选鉴权）：前台商品 / 非遗资料 / 非遗传承详情（/api/{模块}/{数字ID}，同路径的 PUT/DELETE 仍强制鉴权） */
    private static final Pattern GOODS_DETAIL_PATH = Pattern.compile("^/api/goods/\\d+$");
    private static final Pattern CULTURE_DETAIL_PATH = Pattern.compile("^/api/culture/\\d+$");
    private static final Pattern INHERIT_DETAIL_PATH = Pattern.compile("^/api/inherit/\\d+$");

    /** 论坛帖子详情（免登录 GET，可选鉴权；同路径的 PUT/DELETE 仍强制鉴权） */
    private static final Pattern FORUM_DETAIL_PATH = Pattern.compile("^/api/inherit/forum/\\d+$");

    /** 请求头token前缀 */
    private static final String TOKEN_PREFIX = "Bearer ";

    /** 透传用户信息的请求头 */
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 跨域预检请求与白名单接口直接放行
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || WHITE_LIST.contains(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        boolean tokenValid = token != null && jwtUtil.validateToken(token);

        // 免登录的 GET 动态路径（商品 / 非遗资料 / 非遗传承详情）：可选鉴权，未携带或已失效 token 按游客放行
        if (isOptionalAuthPath(request, path) && !tokenValid) {
            return chain.filter(exchange);
        }

        if (!tokenValid) {
            log.warn("token校验失败，拒绝访问：{}", path);
            return writeUnauthorized(exchange.getResponse());
        }

        Long userId = jwtUtil.getUserId(token);
        Integer role = jwtUtil.getRole(token);
        if (userId == null) {
            log.warn("token载荷缺失userId，拒绝访问：{}", path);
            return writeUnauthorized(exchange.getResponse());
        }

        // 剥离客户端可能伪造的用户头后，写入校验过的可信用户信息
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_ROLE);
                })
                .header(HEADER_USER_ID, String.valueOf(userId));
        if (role != null) {
            requestBuilder.header(HEADER_USER_ROLE, String.valueOf(role));
        }
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    /** 可选鉴权的免登录 GET 动态路径：前台商品 / 非遗资料 / 非遗传承 / 论坛帖子详情（有效 token 时透传用户信息） */
    private boolean isOptionalAuthPath(ServerHttpRequest request, String path) {
        return HttpMethod.GET.equals(request.getMethod())
                && (GOODS_DETAIL_PATH.matcher(path).matches()
                        || CULTURE_DETAIL_PATH.matcher(path).matches()
                        || INHERIT_DETAIL_PATH.matcher(path).matches()
                        || FORUM_DETAIL_PATH.matcher(path).matches());
    }

    /** 提取 Bearer token */
    private String resolveToken(String authorization) {
        if (authorization == null || !authorization.startsWith(TOKEN_PREFIX)) {
            return null;
        }
        String token = authorization.substring(TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** 返回统一Result结构的401响应（HTTP 401 + body code=401） */
    private Mono<Void> writeUnauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsBytes(Result.fail(ResultCodeEnum.NOT_LOGIN));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            log.error("序列化401响应失败", e);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 尽早执行鉴权
        return -100;
    }
}
