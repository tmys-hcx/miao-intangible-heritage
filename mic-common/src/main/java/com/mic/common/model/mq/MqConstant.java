package com.mic.common.model.mq;

/**
 * RabbitMQ 常量定义（订单通知消息：mic-goods 生产 → mic-notice 消费）
 * 消息拓扑：主交换机 mic.notice.exchange --路由键 notice.order--> 主队列 mic.order.notice.queue；
 * 消费重试耗尽的消息经死信交换机 mic.notice.dlx --路由键 notice.order.dlq--> 死信队列 mic.order.notice.dlq（人工排查）
 * 说明：常量由生产 / 消费两端共用，保证两侧声明的交换机、队列参数完全一致（Spring AMQP 重复声明幂等无副作用）
 */
public class MqConstant {

    /** 通知主交换机（Direct：订单通知按路由键精确投递） */
    public static final String NOTICE_EXCHANGE = "mic.notice.exchange";

    /** 订单通知队列（mic-notice 消费：买家支付成功 / 卖家发货 / 买家申请退款 / 卖家退款处理 通知落库） */
    public static final String ORDER_NOTICE_QUEUE = "mic.order.notice.queue";

    /** 订单通知路由键（生产者投递与队列绑定共用） */
    public static final String ORDER_NOTICE_ROUTING_KEY = "notice.order";

    /** 通知死信交换机（消费重试耗尽的消息转入，供告警与人工排查） */
    public static final String NOTICE_DLX_EXCHANGE = "mic.notice.dlx";

    /** 订单通知死信队列 */
    public static final String ORDER_NOTICE_DLQ = "mic.order.notice.dlq";

    /** 订单通知死信路由键 */
    public static final String ORDER_NOTICE_DLQ_ROUTING_KEY = "notice.order.dlq";

    private MqConstant() {
    }
}
