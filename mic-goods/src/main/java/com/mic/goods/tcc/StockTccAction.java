package com.mic.goods.tcc;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * 库存预留 TCC 事务接口（Seata TCC 模式，资源：Redis 可用库存 + MySQL 商品库存）
 * 三阶段语义（下单一次请求内闭环）：
 * Try    —— Redis Lua 原子预扣可用库存（avail -= num、frozen += num）并写入 T 状态冻结流水；
 * Confirm —— TC 自动调度：DB 原子落库扣减 stock（含售罄自动下架）、冻结流水 T→C、释放 Redis 冻结；
 * Cancel  —— TC 自动调度：Redis 冻结释放并回补可用（avail += num）、冻结流水 T→R；未 Try 直接 Cancel 时空回滚
 * useTCCFence=true：空回滚 / 悬挂 / 幂等三防由 tcc_fence_log 表 + Seata Fence 拦截器保障
 */
@LocalTCC
public interface StockTccAction {

    /**
     * Try：预扣 Redis 可用库存（库存不足 / Redis 不可用抛 BusinessException，触发全局回滚）
     * @param orderNo 订单编号（预留凭据，下单前先行生成）
     * @param goodsId 商品ID
     * @param num     预扣数量
     */
    @TwoPhaseBusinessAction(
            name = "stockTccAction",
            commitMethod = "commit",
            rollbackMethod = "rollback",
            useTCCFence = false
    )
    boolean tryDeduct(
            @BusinessActionContextParameter(paramName = "orderNo") String orderNo,
            @BusinessActionContextParameter(paramName = "goodsId") Long goodsId,
            @BusinessActionContextParameter(paramName = "num") int num
    );

    boolean commit(BusinessActionContext actionContext);

    boolean rollback(BusinessActionContext actionContext);
}
