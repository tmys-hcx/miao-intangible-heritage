package com.mic.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 获取客户端IP工具（兼容Nginx/网关代理场景）
 */
public class IpUtil {

    private static final String UNKNOWN = "unknown";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private IpUtil() {
    }

    /**
     * 获取客户端真实IP
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String ip = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (isInvalid(ip)) {
            ip = request.getHeader(HEADER_X_REAL_IP);
        }
        if (isInvalid(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时 X-Forwarded-For 为逗号分隔串：取第一个非unknown地址
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return isInvalid(ip) ? UNKNOWN : ip;
    }

    private static boolean isInvalid(String ip) {
        return ip == null || ip.isBlank() || UNKNOWN.equalsIgnoreCase(ip);
    }
}
