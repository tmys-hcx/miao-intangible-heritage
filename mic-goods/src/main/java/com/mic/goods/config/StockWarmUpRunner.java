package com.mic.goods.config;

import com.mic.goods.tcc.StockRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 库存 Redis 池启动预热（TCC 预扣库存）：应用就绪后按不变量 avail = DB stock - 在途冻结
 * 全量刷新已上架商品的可用额度，保证重启 / Redis 数据丢失后库存池与 DB 对齐
 */
@Component
@RequiredArgsConstructor
public class StockWarmUpRunner {

    private final StockRedisService stockRedisService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnStartup() {
        stockRedisService.warmUp();
    }
}
