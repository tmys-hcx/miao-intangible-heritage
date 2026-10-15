package com.mic.goods.feign;

import com.mic.common.model.dto.NoticeCreateDTO;
import com.mic.common.model.feign.FeignConstant;
import com.mic.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 消息通知服务Feign客户端（服务名 mic-notice，经Nacos服务发现调用）：
 * 订单支付 / 发货 / 退款等事件发生时创建通知，接收者 / 标题 / 内容由本服务组装
 */
@FeignClient(value = FeignConstant.SERVICE_NOTICE)
public interface NoticeFeignClient {

    /** 创建通知（内部接口，见 mic-notice NotificationController#create） */
    @PostMapping("/notice/create")
    Result<Void> create(@RequestBody NoticeCreateDTO dto);
}
