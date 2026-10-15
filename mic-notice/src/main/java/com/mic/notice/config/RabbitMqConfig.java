package com.mic.notice.config;

import com.mic.common.model.mq.MqConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置（消费者侧：订单通知消息消费）
 * 消息拓扑（与 mic-goods 生产端声明保持一致，Spring AMQP 重复声明幂等）：
 * 主交换机 mic.notice.exchange --notice.order--> 主队列 mic.order.notice.queue（死信配置指向死信交换机）；
 * 死信交换机 mic.notice.dlx --notice.order.dlq--> 死信队列 mic.order.notice.dlq
 * 消费可靠性：消费失败自动重试 3 次（见 application.yml listener.simple.retry），
 * 耗尽后消息经死信交换机转入死信队列，由 NoticeMqConsumer 告警日志输出（不阻塞后续消息消费）
 */
@Configuration
public class RabbitMqConfig {

    /** 通知主交换机（Direct：订单通知按路由键精确投递） */
    @Bean
    public DirectExchange noticeExchange() {
        return new DirectExchange(MqConstant.NOTICE_EXCHANGE, true, false);
    }

    /** 订单通知队列（含死信配置：消费重试耗尽的消息转入死信交换机） */
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

    /** JSON 消息转换器：消息体按 NoticeCreateDTO 反向还原（与 mic-goods 生产端一致，类位于 mic-common 双端共享） */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
