package com.mic.notice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.NoticeCreateDTO;
import com.mic.notice.model.entity.Notification;
import com.mic.notice.model.vo.NoticeVO;

import java.util.List;

/**
 * 消息通知服务接口
 */
public interface NotificationService extends IService<Notification> {

    /** 创建通知（接收者 / 标题 / 内容由业务服务组装后远程调用；自己触发自己的事件不产生通知） */
    void create(NoticeCreateDTO dto);

    /** 我的通知列表（当前用户收到 + 发出的全部通知，按时间倒序，映射为前端展示VO） */
    List<NoticeVO> listMine(Long userId);

    /** 单条标记已读（仅可标记本人收到的通知，越权 / 已读时幂等无影响） */
    void markRead(Long id, Long userId);

    /** 全部已读（仅本人收到的未读通知；bizType 非空时仅标记该模块） */
    void markAllRead(Long userId, String bizType);
}
