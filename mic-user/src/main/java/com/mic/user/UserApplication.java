package com.mic.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类
 * scanBasePackages说明：除本服务外还需扫描com.mic.common，加载公共模块的JwtUtil、RedisUtil、
 * RedisConfig、MyBatisPlusConfi
 * g、SwaggerConfig、MyMetaObjectHandler、全局异常处理器等组件
 */
@SpringBootApplication(scanBasePackages = {"com.mic.user", "com.mic.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.mic.user.feign")
@MapperScan("com.mic.user.mapper")
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}

