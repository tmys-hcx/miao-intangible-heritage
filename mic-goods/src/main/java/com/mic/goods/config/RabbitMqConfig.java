package com.mic.goods.config;

import com.mic.common.model.mq.MqConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置（生产者侧：订单通知消息投递）
 * 消息拓扑（与 mic-notice 消费端声明保持一致，Spring AMQP 重复声明幂等，任一端先启动均不丢消息）：
 * 主交换机 mic.notice.exchange --notice.order--> 主队列 mic.order.notice.queue；
 * 主队列死信配置 → 死信交换机 mic.notice.dlx --notice.order.dlq--> 死信队列 mic.order.notice.dlq
 * 发送可靠性：publisher-confirm（抵达交换机确认）+ publisher-returns（路由失败回退，见 application.yml），
 * 回调仅记录日志——通知属旁路流程，发送失败不阻断订单主流程
 */
@Slf4j
@Configuration
public class RabbitMqConfig {

    /** 通知主交换机（Direct：订单通知按路由键精确投递） */
    @Bean
    public DirectExchange noticeExchange() {
        return new DirectExchange(MqConstant.NOTICE_EXCHANGE, true, false);
    }

    /** 订单通知队列（含死信配置：消费重试耗尽的消息转入死信交换机，不阻塞主队列） */
    @Bean
    public Queue orderNoticeQueue() {
        return QueueBuilder.durable(MqConstant.ORDER_NOTICE_QUEUE)
                .deadLetterExchange(MqConstant.NOTICE_DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstant.ORDER_NOTICE_DLQ_ROUTING_KEY)
                .build();
    }

    /** 订单通知绑定（路由键 notice.order） */
    @Bean
    public Binding orderNoticeBinding() {
        return BindingBuilder.bind(orderNoticeQueue()).to(noticeExchange()).with(MqConstant.ORDER_NOTICE_ROUTING_KEY);
    }

    /** 通知死信交换机（消费重试耗尽的消息转入，供告警与人工排查） */
    @Bean
    public DirectExchange noticeDlxExchange() {
        return new DirectExchange(MqConstant.NOTICE_DLX_EXCHANGE, true, false);
    }

    /** 订单通知死信队列 */
    @Bean
    public Queue orderNoticeDlq() {
        return QueueBuilder.durable(MqConstant.ORDER_NOTICE_DLQ).build();
    }

    /** 死信绑定（路由键 notice.order.dlq） */
    @Bean
    public Binding orderNoticeDlqBinding() {
        return BindingBuilder.bind(orderNoticeDlq()).to(noticeDlxExchange()).with(MqConstant.ORDER_NOTICE_DLQ_ROUTING_KEY);
    }

    /** JSON 消息转换器：消息体序列化为 NoticeCreateDTO 的 JSON（与 mic-notice 消费端一致，类位于 mic-common 双端共享） */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** 发送模板：应用 application.yml 中 spring.rabbitmq.* 配置（confirm / returns / mandatory），并设置发送结果回调日志 */
    @Bean
    public RabbitTemplate rabbitTemplate(RabbitTemplateConfigurer configurer,
                                         ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate();
        configurer.configure(template, connectionFactory);
        template.setMessageConverter(messageConverter);
        // 消息抵达交换机确认：未抵达（如交换机不存在 / 连接异常）时记录告警
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("订单通知消息未抵达交换机：correlationData={}, cause={}", correlationData, cause);
            }
        });
        // 消息抵达交换机但未匹配队列（如队列尚未声明）时回退：记录告警
        template.setReturnsCallback(returned -> log.error("订单通知消息路由失败：exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}
