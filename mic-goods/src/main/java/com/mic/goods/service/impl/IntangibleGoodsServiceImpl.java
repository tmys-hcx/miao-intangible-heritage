package com.mic.goods.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.vo.StatsStatusCountVO;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.common.util.DateUtil;
import com.mic.common.util.RedisUtil;
import com.mic.common.util.StatsUtil;
import com.mic.goods.mapper.GoodsOrderMapper;
import com.mic.goods.mapper.IntangibleGoodsMapper;
import com.mic.goods.model.dto.GoodsSaveDTO;
import com.mic.goods.model.entity.GoodsOrder;
import com.mic.goods.model.entity.IntangibleGoods;
import com.mic.goods.model.vo.AdminStatsVO;
import com.mic.goods.model.vo.GoodsAuditRecordVO;
import com.mic.goods.service.GoodsCategoryService;
import com.mic.goods.service.IntangibleGoodsService;
import com.mic.goods.tcc.StockRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 非遗商品服务实现
 * 审核相关数据双写 Redis（7天过期，Redis 异常仅告警、不影响数据库主流程）：
 * - miao:goods:submit:{id}        非遗人提交的商品快照（发布 / 修改重新提交时写入）
 * - miao:goods:audit:record:{id}  审核记录（审核结论、打回原因、审核人、审核时间）
 * - miao:goods:audit:pending      待审核商品ID集合（发布时加入，审核 / 删除时移除）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntangibleGoodsServiceImpl extends ServiceImpl<IntangibleGoodsMapper, IntangibleGoods> implements IntangibleGoodsService {

    /** 商品状态：0待审核（新发布 / 修改后重新审核） */
    private static final int STATUS_PENDING = 0;

    /** 商品状态：1审核通过（上架，前台可见） */
    private static final int STATUS_APPROVED = 1;

    /** 商品状态：2审核拒绝（打回，前台不可见，可修改后重新提交审核） */
    private static final int STATUS_REJECTED = 2;

    /** 商品状态：3已下架（非遗人手动下架 / 库存售罄自动下架，前台不可见、不可搜索、不可加购下单） */
    private static final int STATUS_OFF_SHELF = 3;

    /** 订单状态：0待付款（成交额统计排除：未实际支付） */
    private static final int ORDER_STATUS_UNPAID = 0;

    /** 订单状态：4已退款（成交额统计排除：款项已退回） */
    private static final int ORDER_STATUS_REFUNDED = 4;

    /** 订单状态：5已取消（成交额统计排除：交易关闭） */
    private static final int ORDER_STATUS_CANCELLED = 5;

    /** 订单状态：8退款申请中（成交额统计排除：存在退回风险） */
    private static final int ORDER_STATUS_REFUNDING = 8;

    /** 排序方式：最热（按销量即订单数倒序） */
    private static final String SORT_HOT = "hot";

    /** Redis key 前缀：非遗人提交的商品快照（JSON，发布 / 修改重新提交时写入） */
    private static final String REDIS_SUBMIT_KEY_PREFIX = "miao:goods:submit:";

    /** Redis key 前缀：商品审核记录（JSON，审核完成后写入） */
    private static final String REDIS_AUDIT_RECORD_KEY_PREFIX = "miao:goods:audit:record:";

    /** Redis key：待审核商品ID集合（发布 / 修改重新提交时加入，审核 / 删除时移除） */
    private static final String REDIS_PENDING_AUDIT_KEY = "miao:goods:audit:pending";

    /** Redis 商品审核数据保留时长（天）：按审核周期保留，过期自动清理 */
    private static final long REDIS_EXPIRE_DAYS = 7;

    /** 商品分类服务：校验发布 / 修改商品时分类的有效性 */
    private final GoodsCategoryService goodsCategoryService;

    /** Redis 操作工具：缓存提交快照与审核记录（字符串值场景） */
    private final RedisUtil redisUtil;

    /** StringRedisTemplate：RedisUtil 仅封装字符串值操作，待审核集合（Set）直接使用原生模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /** 订单 Mapper：管理端首页统计读取订单量与成交额（跨域直查订单表，避免注入订单 Service 产生循环依赖） */
    private final GoodsOrderMapper goodsOrderMapper;

    /** 库存 Redis 资源池（TCC 预扣库存）：库存增减 / 审核上架时同步维护可用额度，保证 avail + frozen ≈ DB stock */
    private final StockRedisService stockRedisService;

    /**
     * 前台商品分页：已审核商品，支持分类筛选 / 名称、描述或分类名搜索 / 最新·最热排序
     */
    @Override
    public PageResult<IntangibleGoods> pageGoods(PageDto pageDto, Long categoryId, String keyword, String sort) {
        Page<IntangibleGoods> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());

        String normalizedKeyword = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
        boolean hotSort = SORT_HOT.equals(sort);
        // 关键词额外匹配分类名称：一级分类名带出其全部二级子分类ID（商品名称 / 描述保留原有搜索能力）
        List<Long> keywordCategoryIds = goodsCategoryService.matchCategoryIdsByKeyword(normalizedKeyword);

        // 自定义 SQL（承载搜索/销量排序，手动过滤逻辑删除），分页插件自动分页与统计总数
        baseMapper.selectGoodsPage(page, categoryId, normalizedKeyword, keywordCategoryIds, hotSort);
        return PageResult.of(page);
    }

    /**
     * 前台最新上架商品推荐（不分页）：已审核商品按发布时间倒序取前 N 条
     */
    @Override
    public List<IntangibleGoods> listLatestGoods(Long categoryId, String keyword, int limit) {
        String normalizedKeyword = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
        // 关键词额外匹配分类名称：一级分类名带出其全部二级子分类ID（商品名称 / 描述保留原有搜索能力）
        List<Long> keywordCategoryIds = goodsCategoryService.matchCategoryIdsByKeyword(normalizedKeyword);
        // 自定义 SQL 手动过滤逻辑删除，LIMIT 由服务端硬性限制（前台固定取9条）
        return baseMapper.selectLatestGoods(categoryId, normalizedKeyword, keywordCategoryIds, limit);
    }

    /**
     * 商品详情：仅返回已审核商品
     */
    @Override
    public IntangibleGoods getGoodsDetail(Long id) {
        // 逻辑删除自动过滤（is_deleted=0）；未审核通过的商品前台不可见，统一按不存在处理
        IntangibleGoods goods = lambdaQuery()
                .eq(IntangibleGoods::getId, id)
                .eq(IntangibleGoods::getStatus, STATUS_APPROVED)
                .one();
        if (goods == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品不存在或未上架");
        }
        return goods;
    }

    /**
     * 发布商品（非遗人）：校验分类存在后新增，初始状态待审核
     */
    @Override
    public Long publishGoods(GoodsSaveDTO dto, Long publisherId) {
        checkCategoryExists(dto.getCategoryId());

        IntangibleGoods goods = new IntangibleGoods();
        goods.setPublisherId(publisherId);
        goods.setCategoryId(dto.getCategoryId());
        goods.setGoodsName(dto.getGoodsName());
        goods.setGoodsDesc(dto.getGoodsDesc());
        goods.setGoodsImg(dto.getGoodsImg());
        goods.setPrice(dto.getPrice());
        goods.setStock(dto.getStock());
        goods.setDeliveryTime(dto.getDeliveryTime());
        goods.setLimitBuyNum(dto.getLimitBuyNum());
        // 新发布商品进入待审核状态，管理员审核通过后才在前台展示
        goods.setStatus(STATUS_PENDING);
        save(goods);
        // 提交快照与待审核队列同步至 Redis（商品审核界面按待审核状态同步展示）
        cacheSubmitSnapshot(goods);
        addPendingAudit(goods.getId());
        log.info("发布非遗商品成功（待审核）：id={}, goodsName={}, publisherId={}",
                goods.getId(), goods.getGoodsName(), publisherId);
        return goods.getId();
    }

    /**
     * 修改商品（非遗人）：仅可修改本人发布的商品，修改后重新进入待审核
     */
    @Override
    public void updateGoods(Long id, GoodsSaveDTO dto, Long publisherId) {
        IntangibleGoods goods = getOwnGoods(id, publisherId, "修改");
        checkCategoryExists(dto.getCategoryId());

        goods.setCategoryId(dto.getCategoryId());
        goods.setGoodsName(dto.getGoodsName());
        goods.setGoodsDesc(dto.getGoodsDesc());
        goods.setGoodsImg(dto.getGoodsImg());
        goods.setPrice(dto.getPrice());
        goods.setStock(dto.getStock());
        goods.setDeliveryTime(dto.getDeliveryTime());
        goods.setLimitBuyNum(dto.getLimitBuyNum());
        // 内容变更后重新进入待审核：已通过的商品需管理员重新审核，已拒绝的商品可修改后重新提交
        goods.setStatus(STATUS_PENDING);
        updateById(goods);
        // 修改即重新提交：刷新 Redis 提交快照并重新加入待审核队列
        cacheSubmitSnapshot(goods);
        addPendingAudit(id);
        log.info("修改非遗商品成功（重新待审核）：id={}, publisherId={}", id, publisherId);
    }

    /**
     * 删除商品（非遗人）：仅可删除本人发布的商品（逻辑删除）
     */
    @Override
    public void deleteGoods(Long id, Long publisherId) {
        getOwnGoods(id, publisherId, "删除");
        // 逻辑删除（@TableLogic），数据库记录保留，与商品分类的物理删除策略不同
        removeById(id);
        // 同步清理 Redis：提交快照 / 审核记录 / 待审核队列 / 库存池（可用+冻结 key，避免残留脏数据）
        clearRedisCache(id);
        stockRedisService.deletePool(id);
        log.info("删除非遗商品成功（逻辑删除）：id={}, publisherId={}", id, publisherId);
    }

    /**
     * 下架商品（非遗人）：仅可下架本人发布的已上架商品，下架后前台不可见、不可搜索、不可加购 / 下单
     * 下架商品可通过「修改后重新提交审核」再次上架；库存售罄商品由下单扣减库存流程自动下架
     */
    @Override
    public void offShelfGoods(Long id, Long publisherId) {
        // 归属校验：仅本人商品可下架，历史状态不限（下架目标必须是已上架）
        IntangibleGoods goods = getOwnGoods(id, publisherId, "下架");
        // 条件更新抢占（仅命中已上架商品）：防止与管理员审核 / 并发下架操作冲突，未命中时明确提示
        boolean offShelf = lambdaUpdate()
                .eq(IntangibleGoods::getId, id)
                .eq(IntangibleGoods::getStatus, STATUS_APPROVED)
                .set(IntangibleGoods::getStatus, STATUS_OFF_SHELF)
                .update();
        if (!offShelf) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅已上架商品可下架");
        }
        log.info("下架非遗商品成功：id={}, goodsName={}, publisherId={}", id, goods.getGoodsName(), publisherId);
    }

    /**
     * 审核商品（管理员）：审核通过上架 / 审核拒绝打回（前台不可见，需填写不合格原因）
     * 审核结论与备注存入 Redis 审核记录，并将商品移出待审核队列
     */
    @Override
    public void auditGoods(Long id, Integer auditStatus, String remark, Long auditorId) {
        // 兜底校验审核结论（防止内部调用绕开 DTO 注解约束），仅支持：1审核通过 2审核拒绝
        if (auditStatus == null || (auditStatus != STATUS_APPROVED && auditStatus != STATUS_REJECTED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "审核结论仅支持：1审核通过、2审核拒绝");
        }
        // 打回必须填写不合格原因，供非遗人按提示修改后重新提交
        String auditRemark = remark == null ? null : remark.trim();
        if (auditStatus == STATUS_REJECTED && (auditRemark == null || auditRemark.isEmpty())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "打回商品必须填写不合格原因");
        }
        IntangibleGoods goods = getById(id);
        if (goods == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品不存在或已删除");
        }
        // 允许管理员对任意审核状态的商品调整结论（如已上架违规商品可直接打回下架）
        IntangibleGoods update = new IntangibleGoods();
        update.setId(id);
        update.setStatus(auditStatus);
        updateById(update);
        // 审核结论与打回原因写入 Redis，并移出待审核队列
        cacheAuditRecord(goods, auditStatus, auditRemark, auditorId);
        removePendingAudit(id);
        // 审核通过上架：按最新 DB 库存重置 Redis 可用额度（扣除在途冻结，与 TCC 库存池不变量对齐）
        if (auditStatus == STATUS_APPROVED) {
            stockRedisService.resetAvail(id, goods.getStock());
        }
        log.info("商品审核完成：id={}, goodsName={}, status={}（0待审核 1通过 2打回）, auditorId={}",
                id, goods.getGoodsName(), auditStatus, auditorId);
    }

    /**
     * 后台商品分页（管理员）：全部商品（含待审核 / 已拒绝），可按审核状态筛选，按创建时间倒序
     */
    @Override
    public PageResult<IntangibleGoods> pageAdminGoods(PageDto pageDto, Integer auditStatus) {
        Page<IntangibleGoods> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        // 自定义 SQL（LEFT JOIN sys_user 关联发布人账号，手动过滤逻辑删除），分页插件自动分页与统计
        baseMapper.selectAdminGoodsPage(page, auditStatus);
        // 回填 Redis 审核记录（打回原因 / 审核时间），供审核界面展示
        fillAuditInfo(page.getRecords());
        return PageResult.of(page);
    }

    /**
     * 我发布的商品分页（非遗人）：本人发布的全部状态商品，可按状态筛选
     * 审核结果（打回原因 / 审核时间）从 Redis 审核记录回填
     */
    @Override
    public PageResult<IntangibleGoods> pageMyGoods(PageDto pageDto, Long publisherId, Integer status) {
        Page<IntangibleGoods> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        lambdaQuery()
                .eq(IntangibleGoods::getPublisherId, publisherId)
                .eq(status != null, IntangibleGoods::getStatus, status)
                .orderByDesc(IntangibleGoods::getCreateTime)
                .orderByAsc(IntangibleGoods::getId)
                .page(page);
        fillAuditInfo(page.getRecords());
        return PageResult.of(page);
    }

    // ———————————————— Redis 审核数据读写（异常仅告警，不影响数据库主流程） ————————————————

    /** 缓存非遗人提交的商品快照至 Redis（发布 / 修改重新提交时写入，覆盖旧快照，7天过期） */
    private void cacheSubmitSnapshot(IntangibleGoods goods) {
        try {
            redisUtil.setEx(REDIS_SUBMIT_KEY_PREFIX + goods.getId(), JSONUtil.toJsonStr(goods),
                    REDIS_EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis 提交快照写入失败（不影响主流程）：goodsId={}, err={}", goods.getId(), e.getMessage());
        }
    }

    /** 待审核队列同步（Redis Set）：发布 / 修改重新提交时加入 */
    private void addPendingAudit(Long goodsId) {
        try {
            stringRedisTemplate.opsForSet().add(REDIS_PENDING_AUDIT_KEY, goodsId.toString());
        } catch (Exception e) {
            log.warn("Redis 待审核队列加入失败（不影响主流程）：goodsId={}, err={}", goodsId, e.getMessage());
        }
    }

    /** 待审核队列同步（Redis Set）：审核完成时移除 */
    private void removePendingAudit(Long goodsId) {
        try {
            stringRedisTemplate.opsForSet().remove(REDIS_PENDING_AUDIT_KEY, goodsId.toString());
        } catch (Exception e) {
            log.warn("Redis 待审核队列移除失败（不影响主流程）：goodsId={}, err={}", goodsId, e.getMessage());
        }
    }

    /** 缓存审核记录至 Redis：审核结论、打回原因、审核人、审核时间（覆盖旧记录，7天过期） */
    private void cacheAuditRecord(IntangibleGoods goods, Integer auditStatus, String remark, Long auditorId) {
        try {
            GoodsAuditRecordVO record = new GoodsAuditRecordVO();
            record.setGoodsId(goods.getId());
            record.setGoodsName(goods.getGoodsName());
            record.setAuditStatus(auditStatus);
            record.setRemark(remark);
            record.setAuditorId(auditorId);
            record.setAuditTime(DateUtil.now());
            redisUtil.setEx(REDIS_AUDIT_RECORD_KEY_PREFIX + goods.getId(), JSONUtil.toJsonStr(record),
                    REDIS_EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis 审核记录写入失败（不影响主流程）：goodsId={}, err={}", goods.getId(), e.getMessage());
        }
    }

    /** 读取 Redis 审核记录：不存在 / 过期 / 解析失败时返回 null */
    private GoodsAuditRecordVO readAuditRecord(Long goodsId) {
        try {
            String json = redisUtil.get(REDIS_AUDIT_RECORD_KEY_PREFIX + goodsId);
            return (json == null || json.isBlank()) ? null : JSONUtil.toBean(json, GoodsAuditRecordVO.class);
        } catch (Exception e) {
            log.warn("Redis 审核记录读取失败：goodsId={}, err={}", goodsId, e.getMessage());
            return null;
        }
    }

    /** 为商品列表回填审核信息（打回原因 / 审核时间）：待审核商品无审核记录，跳过 */
    private void fillAuditInfo(List<IntangibleGoods> records) {
        if (records == null) {
            return;
        }
        for (IntangibleGoods goods : records) {
            if (goods.getStatus() == null || goods.getStatus() == STATUS_PENDING) {
                continue;
            }
            GoodsAuditRecordVO record = readAuditRecord(goods.getId());
            if (record != null) {
                goods.setAuditRemark(record.getRemark());
                goods.setAuditTime(record.getAuditTime());
            }
        }
    }

    /** 删除商品时清理 Redis 相关数据（提交快照 / 审核记录 / 待审核队列） */
    private void clearRedisCache(Long goodsId) {
        try {
            redisUtil.delete(REDIS_SUBMIT_KEY_PREFIX + goodsId);
            redisUtil.delete(REDIS_AUDIT_RECORD_KEY_PREFIX + goodsId);
            stringRedisTemplate.opsForSet().remove(REDIS_PENDING_AUDIT_KEY, goodsId.toString());
        } catch (Exception e) {
            log.warn("Redis 审核数据清理失败（不影响主流程）：goodsId={}, err={}", goodsId, e.getMessage());
        }
    }

    /** 查询本人发布的商品：不存在或非本人发布时抛出业务异常（含待审核 / 已拒绝商品，便于本人维护） */
    private IntangibleGoods getOwnGoods(Long id, Long publisherId, String action) {
        IntangibleGoods goods = getById(id);
        if (goods == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品不存在或已删除");
        }
        if (!goods.getPublisherId().equals(publisherId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅可" + action + "本人发布的商品");
        }
        return goods;
    }

    // ———————————————— 管理端首页数据统计 ————————————————

    @Override
    public AdminStatsVO adminStats() {
        AdminStatsVO vo = new AdminStatsVO();
        // 1.商品总量与状态分布（0待审核 / 1上架 / 2打回 / 3下架，逻辑删除记录自动排除）
        vo.setGoodsTotal(count());
        vo.setGoodsPending(lambdaQuery().eq(IntangibleGoods::getStatus, STATUS_PENDING).count());
        vo.setGoodsOnSale(lambdaQuery().eq(IntangibleGoods::getStatus, STATUS_APPROVED).count());
        vo.setGoodsRejected(lambdaQuery().eq(IntangibleGoods::getStatus, STATUS_REJECTED).count());
        vo.setGoodsOffShelf(lambdaQuery().eq(IntangibleGoods::getStatus, STATUS_OFF_SHELF).count());
        // 2.订单总量与状态分布（0待付款~8退款申请中，按状态一次分组统计）
        vo.setOrderTotal(goodsOrderMapper.selectCount(null));
        List<StatsStatusCountVO> statusCounts = goodsOrderMapper.selectMaps(new QueryWrapper<GoodsOrder>()
                        .select("order_status AS status", "COUNT(*) AS cnt")
                        .groupBy("order_status")).stream()
                .map(row -> new StatsStatusCountVO(((Number) row.get("status")).intValue(),
                        ((Number) row.get("cnt")).longValue()))
                .toList();
        vo.setOrderStatusCounts(statusCounts);
        // 3.成交金额：已支付口径（排除 0待付款 / 4已退款 / 5已取消 / 8退款申请中）的订单金额合计，无数据返回0
        List<Object> amountSums = goodsOrderMapper.selectObjs(new QueryWrapper<GoodsOrder>()
                .select("IFNULL(SUM(total_price), 0)")
                .notIn("order_status", ORDER_STATUS_UNPAID, ORDER_STATUS_REFUNDED,
                        ORDER_STATUS_CANCELLED, ORDER_STATUS_REFUNDING));
        vo.setPaidAmount(amountSums.isEmpty() ? BigDecimal.ZERO : new BigDecimal(amountSums.get(0).toString()));
        // 4.近7天订单趋势：按天分组统计后补零组装（含今天，日期升序）
        List<Map<String, Object>> rows = goodsOrderMapper.selectMaps(new QueryWrapper<GoodsOrder>()
                .select("DATE_FORMAT(create_time, '%m-%d') AS trend_date", "COUNT(*) AS trend_count")
                .ge("create_time", LocalDate.now().minusDays(6).atStartOfDay())
                .groupBy("DATE_FORMAT(create_time, '%m-%d')"));
        vo.setOrderTrend(StatsUtil.fillLast7Days(StatsUtil.toCountByDate(rows, "trend_date", "trend_count")));
        return vo;
    }

    /**
     * 扣减商品库存（非下单场景兼容入口；下单链路已改走 TCC，Confirm 直调 Mapper 不经过本方法）：
     * 原子扣减防止并发超卖；扣减后库存为 0 的已上架商品自动下架（3），前台立即不可见、不可搜索、不可下单；
     * DB 扣减后同步扣减 Redis 可用额度（同事务内 Redis 失败则整体回滚，保证库存池不变量）
     */
    @Override
    public void deductStock(Long goodsId, int num) {
        if (baseMapper.deductStock(goodsId, num) == 0) {
            throw new BusinessException(ResultCodeEnum.STOCK_NOT_ENOUGH, "商品库存不足，无法下单");
        }
        // 售罄自动下架：条件更新仅命中「已上架且库存=0」，并发下多单时仅扣到 0 的那单触发
        if (baseMapper.offShelfIfSoldOut(goodsId) > 0) {
            log.info("商品库存售罄，已自动下架：goodsId={}", goodsId);
        }
        stockRedisService.reduceAvail(goodsId, num);
        log.info("扣减商品库存成功（DB+Redis）：goodsId={}, num={}", goodsId, num);
    }

    /**
     * 恢复商品库存（超时取消 / 买家取消 / 删除 / 退款成功时调用）：
     * DB 增量恢复后同步回补 Redis 可用额度（库存重新可售）
     */
    @Override
    public void restoreStock(Long goodsId, int num) {
        baseMapper.restoreStock(goodsId, num);
        stockRedisService.addAvail(goodsId, num);
        log.info("恢复商品库存成功（DB+Redis回补）：goodsId={}, num={}", goodsId, num);
    }

    /** 校验商品分类存在（分类不存在或已被删除时拒绝发布 / 修改） */
    private void checkCategoryExists(Long categoryId) {
        if (goodsCategoryService.getById(categoryId) == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品分类不存在");
        }
    }
}
