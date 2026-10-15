package com.mic.notice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.model.dto.NoticeCreateDTO;
import com.mic.notice.config.NoticeWebSocketHandler;
import com.mic.notice.mapper.NotificationMapper;
import com.mic.notice.model.entity.Notification;
import com.mic.notice.model.vo.NoticeVO;
import com.mic.notice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 消息通知服务实现：
 * - 通知创建：非遗文化 / 论坛等业务服务在事件发生时（评论 / 回复）经 Feign 远程调用；
 *   购物订单通知由 mic-goods 经 RabbitMQ 异步投递（本服务消费后同样走 create 落库）
 * - 实时推送：通知落库成功且接收人在线时，经 WebSocket 即时推向顶栏铃铛（免轮询刷新）
 * - 自己触发自己的事件不产生通知（如评论自己的文章 / 回复自己的评论）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    /** 未读 */
    private static final int UNREAD = 0;

    /** 已读 */
    private static final int READ = 1;

    /** 实时推送处理器：向在线接收人推送新通知（无在线会话时静默忽略） */
    private final NoticeWebSocketHandler noticeWebSocketHandler;

    @Override
    public void create(NoticeCreateDTO dto) {
        // 自己触发自己的事件不产生通知（评论自己的文章 / 回复自己的评论）
        if (dto.getSendUserId() != null && Objects.equals(dto.getSendUserId(), dto.getReceiveUserId())) {
            return;
        }

        Notification notification = new Notification();
        notification.setReceiveUserId(dto.getReceiveUserId());
        notification.setSendUserId(dto.getSendUserId());
        notification.setNotifyType(dto.getNotifyType());
        notification.setTitle(dto.getTitle());
        notification.setContent(dto.getContent());
        notification.setBizType(dto.getBizType());
        notification.setBizId(dto.getBizId());
        notification.setIsRead(UNREAD);
        save(notification);

        log.info("通知创建成功：id={}, receiveUserId={}, notifyType={}, bizType={}, bizId={}",
                notification.getId(), dto.getReceiveUserId(), dto.getNotifyType(),
                dto.getBizType(), dto.getBizId());

        // 接收人在线时实时推送（VO 与列表接口输出一致：direction=received、isRead=false；无在线会话 / 推送失败均不影响落库）
        noticeWebSocketHandler.push(dto.getReceiveUserId(), toVO(notification, dto.getReceiveUserId()));
    }

    @Override
    public List<NoticeVO> listMine(Long userId) {
        // 我收到的（receive=我）+ 我发出的（send=我），按时间倒序（同一时间按ID倒序）
        List<Notification> list = lambdaQuery()
                .and(wrapper -> wrapper
                        .eq(Notification::getReceiveUserId, userId)
                        .or()
                        .eq(Notification::getSendUserId, userId))
                .orderByDesc(Notification::getCreateTime)
                .orderByDesc(Notification::getId)
                .list();
        return list.stream().map(notification -> toVO(notification, userId)).toList();
    }

    @Override
    public void markRead(Long id, Long userId) {
        // 仅本人收到的未读通知可标记（越权 / 已读 / 发出的记录点击时条件不命中，幂等无影响）
        boolean updated = lambdaUpdate()
                .eq(Notification::getId, id)
                .eq(Notification::getReceiveUserId, userId)
                .eq(Notification::getIsRead, UNREAD)
                .set(Notification::getIsRead, READ)
                .update();
        log.info("通知标记已读：id={}, userId={}, updated={}", id, userId, updated);
    }

    @Override
    public void markAllRead(Long userId, String bizType) {
        boolean hasBizType = bizType != null && !bizType.isEmpty();
        boolean updated = lambdaUpdate()
                .eq(Notification::getReceiveUserId, userId)
                .eq(Notification::getIsRead, UNREAD)
                .eq(hasBizType, Notification::getBizType, bizType)
                .set(Notification::getIsRead, READ)
                .update();
        log.info("通知全部已读：userId={}, bizType={}, updated={}", userId, hasBizType ? bizType : "全部", updated);
    }

    /** 实体 → 前端展示VO（module / direction / isRead 按当前用户视角映射） */
    private NoticeVO toVO(Notification notification, Long userId) {
        NoticeVO vo = new NoticeVO();
        vo.setId(notification.getId());
        vo.setModule(notification.getBizType());
        boolean received = Objects.equals(notification.getReceiveUserId(), userId);
        vo.setDirection(received ? "received" : "sent");
        // 「我发出的」通知的已读状态属于接收人视角，与当前用户无关，统一按已读返回（不计入我的未读数）
        vo.setIsRead(!received || Objects.equals(notification.getIsRead(), READ));
        vo.setNotifyType(notification.getNotifyType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setBizId(notification.getBizId());
        vo.setCreateTime(notification.getCreateTime());
        return vo;
    }
}
