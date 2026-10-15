package com.mic.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 智能助手服务启动类（Agent）：主要由两个业务主题组成，按包隔离、由两名开发者并行开发——
 * 传承与学习助手（com.mic.agent.study）；购物助手（com.mic.agent.shop）。
 * scanBasePackages说明：除本服务外还需扫描com.mic.common，加载公共模块的MyBatisPlusConfig（分页插件）、
 * MyMetaObjectHandler（createTime/updateTime自动填充）、RedisConfig、全局异常处理器等组件
 */
@SpringBootApplication(scanBasePackages = {"com.mic.agent", "com.mic.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.mic.agent") //扫描两个业务主题下的feign子包
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
