package com.mic.notice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 消息通知服务启动类
 * scanBasePackages说明：除本服务外还需扫描com.mic.common，加载公共模块的MyBatisPlusConfig（分页插件）、
 * MyMetaObjectHandler（createTime/updateTime自动填充）、RedisConfig、全局异常处理器等组件
 */
@SpringBootApplication(scanBasePackages = {"com.mic.notice", "com.mic.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.mic.notice.feign")
@MapperScan("com.mic.notice.mapper")
public class NoticeApplication {
    public static void main(String[] args) {
        SpringApplication.run(NoticeApplication.class, args);
    }
}
