package com.mic.gateway;

import com.mic.common.util.JwtUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

/**
 * 网关启动类，排除webmvc自动配置，gateway使用reactive响应式web
 * @Import(JwtUtil.class)：单独引入公共模块JWT工具（@EnableDiscoveryClient扫描范围外），鉴权过滤器使用
 */
@SpringBootApplication(exclude = {WebMvcAutoConfiguration.class})
@EnableDiscoveryClient
@Import(JwtUtil.class)
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

