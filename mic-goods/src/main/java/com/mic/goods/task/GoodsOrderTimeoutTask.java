package com.mic.goods.task;

import com.mic.goods.service.GoodsOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单定时任务
 * 待付款超时自动取消：每60秒扫描一次，将「0待付款且创建超过15分钟未支付」的订单自动置为 5已取消（终态），
 * 逐条归还下单时锁定的库存、清除 Redis 支付会话（超时标记 / 令牌），
 * 并通过 WebSocket 向电脑端模拟支付页推送超时通知（TIMEOUT），同步卖家订单快照
 * 说明：fixedDelay 保证上一次任务执行完毕后再计时，避免任务堆积；超时阈值由订单服务内 PAY_TIMEOUT_MINUTES 常量统一维护
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoodsOrderTimeoutTask {

    /** 订单服务：扫描并取消超时未支付订单 */
    private final GoodsOrderService goodsOrderService;

    /**
     * 待付款订单超时自动取消：应用启动30秒后首次执行，之后每60秒执行一次
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void cancelTimeoutOrders() {
        try {
            int count = goodsOrderService.cancelTimeoutOrders();
            if (count > 0) {
                log.info("订单超时任务完成：本次自动取消 {} 笔待付款订单", count);
            }
        } catch (Exception e) {
            // 定时任务异常仅告警，避免影响后续周期执行
            log.warn("订单超时取消失败（等待下一周期重试）：err={}", e.getMessage(), e);
        }
    }
}
