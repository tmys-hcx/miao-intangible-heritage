package com.mic.goods.tcc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mic.goods.mapper.GoodsStockFreezeMapper;
import com.mic.goods.mapper.IntangibleGoodsMapper;
import com.mic.goods.model.entity.GoodsStockFreeze;
import com.mic.common.exception.BusinessException;
import com.mic.common.result.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存冻结流水 DB 事务组件（TCC 三阶段的 MySQL 侧原子操作封装）
 * 关键设计：Try 写冻结记录使用 REQUIRES_NEW 独立事务——不随下单本地事务回滚，
 * 保证订单事务失败后 Cancel 仍能凭 T 记录定位并还原 Redis 预扣（否则资源泄漏）
 */
@Service
@RequiredArgsConstructor
public class StockFreezeTxService {

    private final GoodsStockFreezeMapper goodsStockFreezeMapper;
    private final IntangibleGoodsMapper intangibleGoodsMapper;

    /** 查询冻结流水（orderNo+goodsId 唯一键定位） */
    public GoodsStockFreeze get(String orderNo, Long goodsId) {
        return goodsStockFreezeMapper.selectOne(new LambdaQueryWrapper<GoodsStockFreeze>()
                .eq(GoodsStockFreeze::getOrderNo, orderNo)
                .eq(GoodsStockFreeze::getGoodsId, goodsId));
    }

    /**
     * Try：独立事务写入 T 状态冻结流水（Redis 预扣成功后调用）
     * @return true 本次新写入；false 记录已存在（Try 重试，业务幂等）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean insertTried(String xid, String orderNo, Long goodsId, int num) {
        if (get(orderNo, goodsId) != null) {
            return false;
        }
        GoodsStockFreeze freeze = new GoodsStockFreeze();
        freeze.setXid(xid);
        freeze.setOrderNo(orderNo);
        freeze.setGoodsId(goodsId);
        freeze.setNum(num);
        freeze.setStatus(GoodsStockFreeze.STATUS_TRIED);
        try {
            goodsStockFreezeMapper.insert(freeze);
        } catch (DuplicateKeyException e) {
            // 并发下唯一键兜底：视为已预留，幂等返回
            return false;
        }
        return true;
    }

    /**
     * Confirm（DB 部分）：同一本地事务内完成「状态 T→C 抢占 + 库存原子落库扣减 + 售罄自动下架」，
     * 扣减失败（DB 库存与 Redis 池不一致的极端场景）时整体回滚状态回 T，交由 TC 重试
     * @return true 本次完成落库；false 状态已非 T（重复投递，业务幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean commitDb(String orderNo, Long goodsId, int num) {
        if (goodsStockFreezeMapper.updateStatus(orderNo, goodsId,
                GoodsStockFreeze.STATUS_TRIED, GoodsStockFreeze.STATUS_COMMITTED) == 0) {
            return false;
        }
        if (intangibleGoodsMapper.deductStock(goodsId, num) == 0) {
            throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH,
                    "Confirm落库失败：DB库存与Redis预留不一致，等待重试 goodsId=" + goodsId);
        }
        // 售罄自动下架（沿用原下单直扣链路的兜底逻辑：仅命中「已上架且库存=0」）
        intangibleGoodsMapper.offShelfIfSoldOut(goodsId);
        return true;
    }

    /** Confirm（收尾）：Redis 冻结释放完成后置终态 F（C→F，重复投递无副作用） */
    public void markReleased(String orderNo, Long goodsId) {
        goodsStockFreezeMapper.updateStatus(orderNo, goodsId,
                GoodsStockFreeze.STATUS_COMMITTED, GoodsStockFreeze.STATUS_RELEASED);
    }

    /** Cancel：冻结流水作废（T→R，影响行数 0 表示已终结，幂等） */
    public void markRolledBack(String orderNo, Long goodsId) {
        goodsStockFreezeMapper.updateStatus(orderNo, goodsId,
                GoodsStockFreeze.STATUS_TRIED, GoodsStockFreeze.STATUS_ROLLED_BACK);
    }
}
