package com.mic.notice.mq;

import com.mic.common.model.dto.NoticeCreateDTO;
import com.mic.common.model.mq.MqConstant;
import com.mic.notice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单通知消息消费者（RabbitMQ）
 * 消息来源：mic-goods 在订单事件发生时投递 NoticeCreateDTO ——
 * 买家支付成功 → 卖家「新订单」；卖家发货 → 买家「已发货」；买家申请退款 → 卖家「退款申请」；卖家处理退款 → 买家「退款结果」
 * 处理逻辑：异步落库 notification 表（与 Feign 接口 /notice/create 共用创建逻辑）；
 * 消费失败自动重试 3 次（application.yml），耗尽后消息转入死信队列由 onOrderNoticeDead 告警
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeMqConsumer {

    private final NotificationService notificationService;

    /** 消费订单通知消息：落库 notification 表（落库失败抛异常触发自动重试） */
    @RabbitListener(queues = MqConstant.ORDER_NOTICE_QUEUE)
    public void onOrderNotice(NoticeCreateDTO notice) {
        notificationService.create(notice);
        log.info("订单通知消息消费成功（RabbitMQ）：receiveUserId={}, title={}, bizId={}",
                notice.getReceiveUserId(), notice.getTitle(), notice.getBizId());
    }

    /** 死信队列监听：重试耗尽的消息告警输出（消息体完整打印，便于人工排查后补发） */
    @RabbitListener(queues = MqConstant.ORDER_NOTICE_DLQ)
    public void onOrderNoticeDead(NoticeCreateDTO notice) {
        log.error("订单通知消息重试耗尽进入死信队列：receiveUserId={}, title={}, bizId={}",
                notice.getReceiveUserId(), notice.getTitle(), notice.getBizId());
    }
}
