package com.mic.agent.shop.context;

/**
 * 购物助手请求上下文（ThreadLocal）：
 * ChatController 在每次 /api/agent/chat 请求开始时，从 Authorization 头解析出当前登录用户
 * （复用 mic-common 的 JwtUtil，与网关同一密钥）并绑定到本上下文；
 * 绑定期间同一线程内的 @Tool 工具读取用户身份做登录校验，
 * Feign 拦截器（FeignAuthRelayConfig）据此透传 X-User-Id / X-User-Role 可信头给 mic-goods / mic-user，
 * 保证购物车、地址、订单操作的都是当前登录用户本人的数据；
 * 请求结束后由 Controller 在 finally 中 clear()，避免线程复用导致的身份串号。
 */
public final class AgentContext {

    /** 当前线程的请求上下文 */
    private static final ThreadLocal<AgentContext> HOLDER = new ThreadLocal<>();

    /** 当前登录用户ID（token 解析所得；游客为 null） */
    private final Long userId;

    /** 当前登录用户角色：0管理员 1非遗人 2普通用户（token 解析所得；游客为 null） */
    private final Integer role;

    /** 原始 JWT token（会话记忆ID由 Controller 使用） */
    private final String token;

    /** 本次对话内创建订单返回的批次首单编号（createOrder 工具写入，供 Controller 返回前端跳转支付页） */
    private String payOrderNo;

    private AgentContext(Long userId, Integer role, String token) {
        this.userId = userId;
        this.role = role;
        this.token = token;
    }

    /** 绑定当前请求的登录用户信息（未登录时传 null 即可，工具据此返回"请先登录"提示） */
    public static void bind(Long userId, Integer role, String token) {
        HOLDER.set(new AgentContext(userId, role, token));
    }

    /** 获取当前线程的请求上下文（非请求线程返回 null） */
    public static AgentContext current() {
        return HOLDER.get();
    }

    /** 请求结束后清理上下文，防止线程池复用导致的身份串号 */
    public static void clear() {
        HOLDER.remove();
    }

    /** 当前是否已登录（token 有效且带 userId） */
    public boolean loggedIn() {
        return userId != null;
    }

    public Long getUserId() {
        return userId;
    }

    public Integer getRole() {
        return role;
    }

    public String getToken() {
        return token;
    }

    public String getPayOrderNo() {
        return payOrderNo;
    }

    public void setPayOrderNo(String payOrderNo) {
        this.payOrderNo = payOrderNo;
    }
}
