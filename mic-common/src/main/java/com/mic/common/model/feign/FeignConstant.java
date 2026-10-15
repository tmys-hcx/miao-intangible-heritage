package com.mic.common.model.feign;

/**
 * Feign调用常量定义（服务名称常量，与各服务 spring.application.name 一致）
 */
public class FeignConstant {

    public static final String SERVICE_USER = "mic-user";
    public static final String SERVICE_CULTURE = "mic-culture";
    public static final String SERVICE_GOODS = "mic-goods";
    public static final String SERVICE_INHERIT = "mic-inherit-reserve";
    public static final String SERVICE_AGENT = "mic-agent";
    public static final String SERVICE_FILE = "mic-file";
    public static final String SERVICE_NOTICE = "mic-notice";

    private FeignConstant() {
    }
}
