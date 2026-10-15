package com.mic.goods.tcc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mic.goods.mapper.GoodsStockFreezeMapper;
import com.mic.goods.mapper.IntangibleGoodsMapper;
import com.mic.goods.model.entity.GoodsStockFreeze;
import com.mic.goods.model.entity.IntangibleGoods;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 库存 Redis 资源池（TCC 预扣库存的数据源）
 * key 规范：goods:stock:avail:{goodsId} 可用库存 / goods:stock:frozen:{goodsId} 在途冻结
 * 不变量：avail + frozen ≈ DB stock（Confirm 落库后 DB 与 avail 同步减扣，等式保持）
 * 原子性：预扣与释放均由 Lua 脚本保证单命令原子，杜绝并发超卖 / 冻结数为负
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRedisService {

    private static final String AVAIL_KEY_PREFIX = "goods:stock:avail:";
    private static final String FROZEN_KEY_PREFIX = "goods:stock:frozen:";

    /** Try 返回码：可用库存 key 不存在（需 DB 回源预热后重试） */
    public static final long RESULT_KEY_MISSING = -1L;
    /** Try 返回码：可用库存不足（预扣失败） */
    public static final long RESULT_NOT_ENOUGH = -2L;

    private final StringRedisTemplate stringRedisTemplate;
    private final IntangibleGoodsMapper intangibleGoodsMapper;
    private final GoodsStockFreezeMapper goodsStockFreezeMapper;

    /** Try 脚本：校验并原子预扣（avail -= num、frozen += num） */
    private static final DefaultRedisScript<Long> TRY_SCRIPT = new DefaultRedisScript<>();
    /** 二阶段释放脚本：frozen -= num，Cancel 时同步 avail += num */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>();

    static {
        TRY_SCRIPT.setLocation(new ClassPathResource("lua/stock_try.lua"));
        TRY_SCRIPT.setResultType(Long.class);
        RELEASE_SCRIPT.setLocation(new ClassPathResource("lua/stock_release.lua"));
        RELEASE_SCRIPT.setResultType(Long.class);
    }

    /**
     * TCC Try：Redis 原子预扣可用库存
     * @return -1 key 未初始化（调用方回源预热后重试一次）；-2 库存不足；>=0 预扣成功后的剩余可用额度
     */
    public long tryDeduct(Long goodsId, int num) {
        Long result = stringRedisTemplate.execute(TRY_SCRIPT,
                List.of(availKey(goodsId), frozenKey(goodsId)), String.valueOf(num));
        return result == null ? RESULT_KEY_MISSING : result;
    }

    /** TCC Confirm：释放冻结（不回补可用，DB 已同步落扣） */
    public void releaseOnCommit(Long goodsId, int num) {
        release(goodsId, num, 0);
    }

    /** TCC Cancel：释放冻结并回补可用库存（预扣作废，库存重新可售） */
    public void restoreOnCancel(Long goodsId, int num) {
        release(goodsId, num, 1);
    }

    /** 业务归还（超时取消 / 买家取消 / 删除 / 退款的 DB restoreStock 配套）：可用库存回补 */
    public void addAvail(Long goodsId, int num) {
        stringRedisTemplate.opsForValue().increment(availKey(goodsId), num);
    }

    /** 非下单场景直接扣减（deductStock 配套）：可用库存同步扣减 */
    public void reduceAvail(Long goodsId, int num) {
        stringRedisTemplate.opsForValue().decrement(availKey(goodsId), num);
    }

    /** 清理库存池（商品删除时调用）：移除可用 / 冻结两个 key，避免残留脏数据 */
    public void deletePool(Long goodsId) {
        stringRedisTemplate.delete(List.of(availKey(goodsId), frozenKey(goodsId)));
    }

    /**
     * 回源初始化（懒加载兜底）：Try 返回 -1 时按 DB 不变量补写 avail（仅 key 仍不存在时写入，避免覆盖并发预扣）
     * @return true 初始化成功；false 商品不存在或无库存语义
     */
    public boolean initAvailIfAbsent(Long goodsId) {
        IntangibleGoods goods = intangibleGoodsMapper.selectById(goodsId);
        if (goods == null) {
            return false;
        }
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(availKey(goodsId),
                String.valueOf(calcAvail(goods.getStock(), goodsId)));
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 重置可用库存（商品编辑库存 / 审核上架 / 启动预热）：强制按最新 DB stock - 在途冻结重算 avail
     */
    public void resetAvail(Long goodsId, int stock) {
        stringRedisTemplate.opsForValue().set(availKey(goodsId),
                String.valueOf(Math.max(0, stock - goodsStockFreezeMapper.sumPendingFrozen(goodsId))));
    }

    /**
     * 启动全量预热：所有已上架（status=1）商品按不变量刷新 avail（一次性加载全部在途冻结，避免逐商品查询）
     */
    public void warmUp() {
        try {
            Map<Long, Integer> frozenMap = loadPendingFrozenMap();
            List<IntangibleGoods> onSale = intangibleGoodsMapper.selectList(
                    new LambdaQueryWrapper<IntangibleGoods>().eq(IntangibleGoods::getStatus, 1));
            for (IntangibleGoods goods : onSale) {
                int avail = Math.max(0, goods.getStock() - frozenMap.getOrDefault(goods.getId(), 0));
                stringRedisTemplate.opsForValue().set(availKey(goods.getId()), String.valueOf(avail));
            }
            log.info("库存Redis预热完成：已上架商品 {} 个", onSale.size());
        } catch (Exception e) {
            // Redis 不可用时不阻断启动：下单 Try 的 -1 回源兜底 + Redis 故障快速失败仍能保持正确性
            log.error("库存Redis预热失败（下单将依赖懒加载回源，请检查 Redis）", e);
        }
    }

    /** 按 DB 不变量计算可用库存：stock - 未终结（T）冻结总量，下限 0 */
    private int calcAvail(int stock, Long goodsId) {
        return Math.max(0, stock - goodsStockFreezeMapper.sumPendingFrozen(goodsId));
    }

    private void release(Long goodsId, int num, int backToAvail) {
        stringRedisTemplate.execute(RELEASE_SCRIPT,
                List.of(availKey(goodsId), frozenKey(goodsId)), String.valueOf(num), String.valueOf(backToAvail));
    }

    private Map<Long, Integer> loadPendingFrozenMap() {
        return goodsStockFreezeMapper.listAllPendingFrozen().stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row.get("goods_id")).longValue(),
                        row -> ((Number) row.get("frozen_num")).intValue()));
    }

    private String availKey(Long goodsId) {
        return AVAIL_KEY_PREFIX + goodsId;
    }

    private String frozenKey(Long goodsId) {
        return FROZEN_KEY_PREFIX + goodsId;
    }
}
