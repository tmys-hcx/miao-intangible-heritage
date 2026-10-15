package com.mic.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 文件服务启动类
 * scanBasePackages说明：除本服务外还需扫描com.mic.common，加载公共模块的MyBatisPlusConfig（分页插件）、
 * MyMetaObjectHandler（createTime/updateTime自动填充）、RedisConfig、全局异常处理器等组件
 */
@SpringBootApplication(scanBasePackages = {"com.mic.file", "com.mic.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.mic.file.feign")
public class FileApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
