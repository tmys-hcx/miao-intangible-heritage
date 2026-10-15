package com.mic.goods.tcc;

import com.mic.common.exception.BusinessException;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.model.entity.GoodsStockFreeze;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.springframework.stereotype.Component;

/**
 * 库存预留 TCC 事务实现（配合 StockTccAction 接口，由 Seata TCC 代理编排三阶段）
 * 资源操作顺序经过崩溃安全设计：
 * Try   —— 先 Redis 原子预扣（失败即抛，无资源残留），成功后独立事务写 T 冻结流水（Cancel 的补偿凭据）；
 * Confirm —— DB 落库（状态抢占 T→C 与扣减同事务，失败整体回滚重试）→ 释放 Redis 冻结 → 置终态 F；
 *            重试时按状态续作（C 表示 DB 已扣仅差释放，直接补释放），全程幂等无双扣
 * Cancel —— 仅存在 T 记录才还原 Redis（无记录=空回滚由 Fence 兜底；C/F 表示 DB 已落扣不可回退）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockTccActionImpl implements StockTccAction {

    private final StockRedisService stockRedisService;
    private final StockFreezeTxService stockFreezeTxService;

    @Override
    public boolean tryDeduct(
            // 参数上下文注解必须同时标在实现类方法上：Spring Boot 3 默认 CGLIB 代理下 Seata 提取
            // @BusinessActionContextParameter 反射的是被调用的实现类方法（无接口回退），否则二阶段拿不到参数
            @BusinessActionContextParameter(paramName = "orderNo") String orderNo,
            @BusinessActionContextParameter(paramName = "goodsId") Long goodsId,
            @BusinessActionContextParameter(paramName = "num") int num) {
        // 1. Redis 原子预扣：key 未初始化时按 DB 不变量回源一次并重试（懒加载兜底）
        long remain = stockRedisService.tryDeduct(goodsId, num);
        if (remain == StockRedisService.RESULT_KEY_MISSING) {
            if (!stockRedisService.initAvailIfAbsent(goodsId)) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品不存在，无法预扣库存");
            }
            remain = stockRedisService.tryDeduct(goodsId, num);
        }
        if (remain == StockRedisService.RESULT_NOT_ENOUGH) {
            // 预扣失败此时未写任何流水、未占资源，直接抛出触发全局回滚（订单本地事务同步回滚，无残留）
            throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH, "商品库存不足，请下单稍后再试");
        }
        if (remain < 0) {
            throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH, "库存预扣失败，请稍后再试");
        }

        // 2. 独立事务写 T 冻结流水：写流水异常时主动回滚Redis，修复Redis预扣成功但无流水的漏洞
        boolean inserted;
        try {
            inserted = stockFreezeTxService.insertTried(RootContext.getXID(), orderNo, goodsId, num);
        } catch (Exception e) {
            log.error("Try阶段写入冻结流水异常，回滚Redis预扣: orderNo={}", orderNo, e);
            stockRedisService.restoreOnCancel(goodsId, num);
            throw e;
        }

        if (!inserted) {
            log.info("库存预留流水已存在（Try 重试幂等）：orderNo={}, goodsId={}", orderNo, goodsId);
        }
        log.info("TCC Try 预扣库存成功：orderNo={}, goodsId={}, num={}, Redis剩余={}",
                orderNo, goodsId, num, remain);
        return true;
    }

    @Override
    public boolean commit(BusinessActionContext actionContext) {
        String orderNo = str(actionContext, "orderNo");
        Long goodsId = lng(actionContext, "goodsId");
        Integer num = integer(actionContext, "num");

        // 参数校验，防止空值进入业务逻辑
        if (orderNo == null || goodsId == null || num == null || num <= 0) {
            log.error("TCC Commit 参数缺失或非法！xid={}, orderNo={}, goodsId={}, num={}",
                    actionContext.getXid(), orderNo, goodsId, num);
            return true;
        }

        GoodsStockFreeze freeze = stockFreezeTxService.get(orderNo, goodsId);
        if (freeze == null) {
            // Try 未走到写流水即失败（空回滚先行），无资源需要提交
            log.warn("TCC Confirm 空提交（无冻结流水）：xid={}, orderNo={}", actionContext.getXid(), orderNo);
            return true;
        }
        if (GoodsStockFreeze.STATUS_RELEASED.equals(freeze.getStatus())) {
            return true; // 已终态，重复投递幂等
        }
        if (GoodsStockFreeze.STATUS_ROLLED_BACK.equals(freeze.getStatus())) {
            log.warn("TCC Confirm 遇到已取消流水（Fence 保证互斥，理论不可达）：orderNo={}", orderNo);
            return true;
        }

        // T：DB 落库扣减（状态抢占 T→C 与扣减同事务，失败回滚状态重试）；C：上次已落库，直接续作释放
        if (GoodsStockFreeze.STATUS_TRIED.equals(freeze.getStatus())) {
            stockFreezeTxService.commitDb(orderNo, goodsId, num);
        }
        stockRedisService.releaseOnCommit(goodsId, num); // frozen -= num（不回补 avail，DB 已同步落扣）
        stockFreezeTxService.markReleased(orderNo, goodsId);
        log.info("TCC Confirm 落库扣减完成：orderNo={}, goodsId={}, num={}", orderNo, goodsId, num);
        return true;
    }

    @Override
    public boolean rollback(BusinessActionContext actionContext) {
        String orderNo = str(actionContext, "orderNo");
        Long goodsId = lng(actionContext, "goodsId");
        Integer num = integer(actionContext, "num");

        // 参数校验，防止空值进入业务逻辑
        if (orderNo == null || goodsId == null || num == null || num <= 0) {
            log.error("TCC Rollback 参数缺失或非法！xid={}, orderNo={}, goodsId={}, num={}",
                    actionContext.getXid(), orderNo, goodsId, num);
            return true;
        }

        GoodsStockFreeze freeze = stockFreezeTxService.get(orderNo, goodsId);
        if (freeze == null) {
            // 空回滚：Try 未执行到资源预扣（如库存不足先行抛错），无需还原，Fence 已记录防悬挂
            log.info("TCC Cancel 空回滚（无冻结流水）：xid={}, orderNo={}", actionContext.getXid(), orderNo);
            return true;
        }
        if (GoodsStockFreeze.STATUS_TRIED.equals(freeze.getStatus())) {
            stockRedisService.restoreOnCancel(goodsId, num); // avail += num、frozen -= num
            stockFreezeTxService.markRolledBack(orderNo, goodsId);
            log.info("TCC Cancel 还原 Redis 预扣完成：orderNo={}, goodsId={}, num={}", orderNo, goodsId, num);
            return true;
        }
        if (GoodsStockFreeze.STATUS_COMMITTED.equals(freeze.getStatus())) {
            // DB 已落库扣减，不可回退库存；残留冻结由 Confirm 重试链路补释放（此处放行，返回成功）
            log.warn("TCC Cancel 遇到已提交待释放流水（不可回退，交由 Confirm 重试续作）：orderNo={}", orderNo);
            return true;
        }
        // F / R 终态：重复投递幂等
        log.info("TCC Cancel 幂等跳过（流水已终态 {}）：orderNo={}", freeze.getStatus(), orderNo);
        return true;
    }

    /** 从二阶段上下文取字符串参数 */
    private String str(BusinessActionContext ctx, String key) {
        Object value = ctx.getActionContext(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 从二阶段上下文取 Long 参数，增加空值保护，修复 NumberFormatException: "null" */
    private Long lng(BusinessActionContext ctx, String key) {
        Object value = ctx.getActionContext(key);
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    /** 从二阶段上下文取 Integer 参数，增加空值保护，修复 NumberFormatException: "null" */
    private Integer integer(BusinessActionContext ctx, String key) {
        Object value = ctx.getActionContext(key);
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }
}
