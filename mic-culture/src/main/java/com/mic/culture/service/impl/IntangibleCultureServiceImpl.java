package com.mic.culture.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.common.util.DateUtil;
import com.mic.common.util.RedisUtil;
import com.mic.common.util.StatsUtil;
import com.mic.culture.mapper.IntangibleCultureMapper;
import com.mic.culture.model.dto.CultureSaveDTO;
import com.mic.culture.model.entity.IntangibleCulture;
import com.mic.culture.model.vo.AdminStatsVO;
import com.mic.culture.model.vo.CultureAuditRecordVO;
import com.mic.culture.service.IntangibleCultureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 非遗文化资料服务实现：前台公开查询（分页 / 详情）+ 发布 / 修改 / 删除（仅非遗人操作本人发布资料）
 * + 管理员审核（1审核通过发布 / 2审核驳回）、后台分页与前台排版管理（拖拽保存展示顺序）
 * 审核相关数据双写 Redis（7天过期，Redis 异常仅告警、不影响数据库主流程）：
 * - miao:culture:submit:{id}        非遗人提交的资料快照（发布 / 修改重新提交时写入）
 * - miao:culture:audit:record:{id}  审核记录（审核结论、驳回原因、审核人、审核时间）
 * - miao:culture:audit:pending      待审核资料ID集合（发布时加入，审核 / 删除时移除）
 * 前台公开数据缓存（Redis 异常仅告警降级查库，发布 / 修改 / 删除 / 审核后统一失效防脏读）：
 * - miao:culture:page:{页码}:{条数}:{排序}:{关键词}  前台分页查询结果（5分钟过期）
 * - miao:culture:detail:{id}                        已发布资料详情（10分钟过期）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntangibleCultureServiceImpl extends ServiceImpl<IntangibleCultureMapper, IntangibleCulture>
        implements IntangibleCultureService {

    /** 角色：1非遗人（0管理员、2普通用户） */
    private static final int ROLE_HERITAGE = 1;

    /** 内容状态：0待审核（新发布 / 修改后重新审核） */
    private static final int STATUS_PENDING = 0;

    /** 内容状态：1已发布（审核通过，前台公开分页仅展示该状态） */
    private static final int STATUS_PUBLISHED = 1;

    /** 内容状态：2审核驳回（前台不可见，可修改后重新提交审核） */
    private static final int STATUS_REJECTED = 2;

    /** Redis key 前缀：非遗人提交的资料快照（JSON，发布 / 修改重新提交时写入） */
    private static final String REDIS_SUBMIT_KEY_PREFIX = "miao:culture:submit:";

    /** Redis key 前缀：资料审核记录（JSON，审核完成后写入） */
    private static final String REDIS_AUDIT_RECORD_KEY_PREFIX = "miao:culture:audit:record:";

    /** Redis key：待审核资料ID集合（发布 / 修改重新提交时加入，审核 / 删除时移除） */
    private static final String REDIS_PENDING_AUDIT_KEY = "miao:culture:audit:pending";

    /** Redis 资料审核数据保留时长（天）：按审核周期保留，过期自动清理 */
    private static final long REDIS_EXPIRE_DAYS = 7;

    /** Redis key 前缀：前台公开分页缓存（JSON，key 尾段为页码 / 条数 / 排序 / 关键词） */
    private static final String REDIS_PAGE_CACHE_PREFIX = "miao:culture:page:";

    /** Redis key 前缀：前台已发布资料详情缓存（JSON） */
    private static final String REDIS_DETAIL_CACHE_PREFIX = "miao:culture:detail:";

    /** 前台分页缓存基础时长（秒）：5 分钟，叠加随机抖动防雪崩 */
    private static final long PAGE_CACHE_SECONDS = 300;

    /** 前台详情缓存基础时长（秒）：10 分钟，叠加随机抖动防雪崩 */
    private static final long DETAIL_CACHE_SECONDS = 600;

    /** 缓存过期随机抖动上限（秒）：叠加在基础时长上，避免同批 key 集中过期 */
    private static final int CACHE_JITTER_SECONDS = 60;

    /** Redis 操作工具：缓存提交快照与审核记录（字符串值场景） */
    private final RedisUtil redisUtil;

    /** StringRedisTemplate：RedisUtil 仅封装字符串值操作，待审核集合（Set）直接使用原生模板 */
    private final StringRedisTemplate stringRedisTemplate;

    /** Jackson 序列化器：缓存 JSON 与接口出参共用同一套 LocalDateTime 格式（yyyy-MM-dd HH:mm:ss） */
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<IntangibleCulture> pagePublished(PageDto pageDto, String keyword, String sortBy) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        boolean hot = "hot".equals(sortBy);
        boolean custom = "custom".equals(sortBy);

        // 优先读 Redis 分页缓存（key 含页码 / 条数 / 排序 / 关键词，条件组合不同即不同缓存）
        String cacheKey = REDIS_PAGE_CACHE_PREFIX + pageDto.getCurrent() + ":" + pageDto.getSize()
                + ":" + (custom ? "custom" : hot ? "hot" : "new") + ":" + (kw == null ? "-" : kw);
        PageResult<IntangibleCulture> cached = readCache(cacheKey,
                new TypeReference<PageResult<IntangibleCulture>>() {});
        if (cached != null) {
            return cached;
        }

        // 缓存未命中：查库（前台仅展示已发布资料；关键词匹配标题或内容；
        // sortBy=hot 按浏览量倒序；sortBy=custom 按管理员排版顺序（sort_order 倒序，未排版的按发布时间倒序排后））
        Page<IntangibleCulture> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        lambdaQuery()
                .eq(IntangibleCulture::getStatus, STATUS_PUBLISHED)
                .and(kw != null,
                        wrapper -> wrapper.like(IntangibleCulture::getTitle, kw)
                                .or()
                                .like(IntangibleCulture::getContent, kw))
                .orderByDesc(custom, IntangibleCulture::getSortOrder)
                .orderByDesc(hot, IntangibleCulture::getViewCount)
                .orderByDesc(!hot, IntangibleCulture::getCreateTime)
                .page(page);

        // 回填缓存（5 分钟 + 随机抖动），相同条件的后续访问直接命中
        PageResult<IntangibleCulture> result = PageResult.of(page);
        writeCache(cacheKey, result, PAGE_CACHE_SECONDS);

        return result;
    }

    @Override
    public IntangibleCulture getDetail(Long id, Long userId) {
        // 已发布资料详情优先走 Redis 缓存（仅缓存公开可见的已发布资料，未发布预览不写缓存）
        String cacheKey = REDIS_DETAIL_CACHE_PREFIX + id;
        IntangibleCulture cached = readCache(cacheKey, new TypeReference<IntangibleCulture>() {});
        if (cached != null) {
            // 缓存命中：浏览量仍以数据库原子自增为准（保证 hot 排序与计数准确），缓存值同步 +1 并续期
            lambdaUpdate().eq(IntangibleCulture::getId, id).setSql("view_count = view_count + 1").update();
            cached.setViewCount((cached.getViewCount() == null ? 0L : cached.getViewCount()) + 1L);
            writeCache(cacheKey, cached, DETAIL_CACHE_SECONDS);
            return cached;
        }

        IntangibleCulture culture = getById(id);
        if (culture == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "非遗资料不存在或已删除");
        }
        // 未发布资料（待审核 / 已驳回）仅发布者本人可预览，对其他访问者按不存在处理；预览不计入浏览量
        if (!Objects.equals(culture.getStatus(), STATUS_PUBLISHED)) {
            if (!Objects.equals(culture.getPublisherId(), userId)) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "非遗资料不存在或已删除");
            }
            return culture;
        }
        // 已发布资料浏览量 +1：数据库原子自增避免并发覆盖，返回自增后的最新值
        lambdaUpdate().eq(IntangibleCulture::getId, id).setSql("view_count = view_count + 1").update();
        culture.setViewCount((culture.getViewCount() == null ? 0L : culture.getViewCount()) + 1L);
        // 回填详情缓存（10 分钟 + 随机抖动）
        writeCache(cacheKey, culture, DETAIL_CACHE_SECONDS);

        return culture;
    }

    @Override
    public PageResult<IntangibleCulture> pageMy(Long userId, Integer role, PageDto pageDto) {
        checkHeritageRole(role);

        Page<IntangibleCulture> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        // 本人的全部资料（含待审核 / 已驳回），按创建时间倒序
        lambdaQuery()
                .eq(IntangibleCulture::getPublisherId, userId)
                .orderByDesc(IntangibleCulture::getCreateTime)
                .page(page);
        // 回填 Redis 审核记录（驳回原因 / 审核时间），供非遗人按提示修改后重新提交
        fillAuditInfo(page.getRecords());

        return PageResult.of(page);
    }

    @Override
    public PageResult<IntangibleCulture> pageAdminCulture(PageDto pageDto, Integer auditStatus) {
        Page<IntangibleCulture> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        // 自定义 SQL（LEFT JOIN sys_user 关联发布人账号，手动过滤逻辑删除），分页插件自动分页与统计
        baseMapper.selectAdminCulturePage(page, auditStatus);
        // 回填 Redis 审核记录（驳回原因 / 审核时间），供审核界面展示
        fillAuditInfo(page.getRecords());
        return PageResult.of(page);
    }

    @Override
    public Long publish(Long userId, Integer role, CultureSaveDTO dto) {
        checkHeritageRole(role);

        IntangibleCulture culture = new IntangibleCulture();
        culture.setPublisherId(userId);
        culture.setTitle(dto.getTitle());
        culture.setContent(dto.getContent());
        culture.setImgUrl(dto.getImgUrl());
        // 无视频统一存空串，便于后续修改时清空原有视频链接
        culture.setVideoUrl(StringUtils.hasText(dto.getVideoUrl()) ? dto.getVideoUrl() : "");
        // 无封面统一存空串，便于修改时清空旧封面；前台卡片按「图片 > 截帧封面 > 视频首帧」兜底展示
        culture.setCoverUrl(StringUtils.hasText(dto.getCoverUrl()) ? dto.getCoverUrl() : "");
        // 新发布资料进入待审核状态，管理员审核通过后才在前台展示
        culture.setStatus(STATUS_PENDING);
        culture.setViewCount(0L);
        save(culture);
        // 提交快照与待审核队列同步至 Redis（资料审核界面按待审核状态同步展示）
        cacheSubmitSnapshot(culture);
        addPendingAudit(culture.getId());
        // 失效前台缓存（新资料为待审核状态前台不可见，统一清理为保守策略，防止未来改为直接发布时脏读）
        clearPublishedCache(culture.getId());

        log.info("非遗资料发布成功（待审核）：id={}, publisherId={}", culture.getId(), userId);
        return culture.getId();
    }

    @Override
    public void updateCulture(Long id, Long userId, Integer role, CultureSaveDTO dto) {
        checkHeritageRole(role);
        checkOwnership(id, userId);

        IntangibleCulture update = new IntangibleCulture();
        update.setId(id);
        update.setTitle(dto.getTitle());
        update.setContent(dto.getContent());
        update.setImgUrl(dto.getImgUrl());
        update.setVideoUrl(StringUtils.hasText(dto.getVideoUrl()) ? dto.getVideoUrl() : "");
        // 封面随视频联动存空串清理由前端提交（替换视频重新截帧 / 无封面为空）
        update.setCoverUrl(StringUtils.hasText(dto.getCoverUrl()) ? dto.getCoverUrl() : "");
        // 内容变更后重新进入待审核：已发布的资料需管理员重新审核，已驳回的资料可修改后重新提交
        update.setStatus(STATUS_PENDING);
        updateById(update);
        // 修改即重新提交：刷新 Redis 提交快照并重新加入待审核队列
        cacheSubmitSnapshot(getById(id));
        addPendingAudit(id);
        // 失效前台缓存（修改后资料回到待审核、从前台暂时下架，旧已发布数据不可再展示）
        clearPublishedCache(id);

        log.info("非遗资料修改成功（重新待审核）：id={}, userId={}", id, userId);
    }

    @Override
    public void removeCulture(Long id, Long userId, Integer role) {
        checkHeritageRole(role);
        checkOwnership(id, userId);
        // @TableLogic 逻辑删除：is_deleted置1，数据保留
        removeById(id);
        // 同步清理 Redis：提交快照 / 审核记录 / 待审核队列
        clearRedisCache(id);
        // 失效前台缓存（资料已删除，列表与详情不可再展示旧数据）
        clearPublishedCache(id);

        log.info("非遗资料删除成功：id={}, userId={}", id, userId);
    }

    @Override
    public void auditCulture(Long id, Integer auditStatus, String remark, Long auditorId) {
        // 兑底校验审核结论（防止内部调用绕开 DTO 注解约束），仅支持：1审核通过 2审核拒绝
        if (auditStatus == null || (auditStatus != STATUS_PUBLISHED && auditStatus != STATUS_REJECTED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "审核结论仅支持：1审核通过、2审核拒绝");
        }
        // 驳回必须填写不合格原因，供非遗人按提示修改后重新提交
        String auditRemark = remark == null ? null : remark.trim();
        if (auditStatus == STATUS_REJECTED && (auditRemark == null || auditRemark.isEmpty())) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "驳回资料必须填写不合格原因");
        }
        IntangibleCulture culture = getById(id);
        if (culture == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "非遗资料不存在或已删除");
        }
        // 允许管理员对任意审核状态的资料调整结论（如已发布违规资料可直接驳回下架）
        IntangibleCulture update = new IntangibleCulture();
        update.setId(id);
        update.setStatus(auditStatus);
        updateById(update);
        // 审核结论与驳回原因写入 Redis，并移出待审核队列
        cacheAuditRecord(culture, auditStatus, auditRemark, auditorId);
        removePendingAudit(id);
        // 失效前台缓存（审核通过进入前台 / 驳回下架，均需立即反映到列表与详情）
        clearPublishedCache(id);
        log.info("非遗资料审核完成：id={}, title={}, status={}（0待审核 1已发布 2已驳回）, auditorId={}",
                id, culture.getTitle(), auditStatus, auditorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSortOrder(List<Long> ids) {
        // 入参兑底校验（防止内部调用绕开 DTO 注解约束）
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "排序文章列表不能为空");
        }
        // 数组顺序即前台展示顺序：第一条排最前（sort_order 取最大值），依序递减；仅更新已发布文章
        int size = ids.size();
        for (int i = 0; i < size; i++) {
            Long id = ids.get(i);
            if (id == null) {
                continue;
            }
            lambdaUpdate()
                    .eq(IntangibleCulture::getId, id)
                    .eq(IntangibleCulture::getStatus, STATUS_PUBLISHED)
                    .set(IntangibleCulture::getSortOrder, (long) (size - i))
                    .update();
        }
        // 排版变化影响全部分页组合的展示顺序：失效前台分页缓存（保存后立即生效）
        clearPublishedCache(null);
        log.info("非遗文化排版已保存：文章数={}", size);
    }

    /** 校验操作角色：发布 / 修改 / 删除非遗资料仅非遗人可操作 */
    private void checkHeritageRole(Integer role) {
        if (role == null || role != ROLE_HERITAGE) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅非遗人可发布或管理非遗资料");
        }
    }

    /** 校验资料存在性与归属：仅本人发布的资料可修改 / 删除 */
    private void checkOwnership(Long id, Long userId) {
        IntangibleCulture culture = getById(id);
        if (culture == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "非遗资料不存在或已删除");
        }
        if (!Objects.equals(culture.getPublisherId(), userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅可操作本人发布的非遗资料");
        }
    }

    // ———————————————— Redis 审核数据读写（异常仅告警，不影响数据库主流程） ————————————————

    /** 缓存非遗人提交的资料快照至 Redis（发布 / 修改重新提交时写入，覆盖旧快照，7天过期） */
    private void cacheSubmitSnapshot(IntangibleCulture culture) {
        try {
            redisUtil.setEx(REDIS_SUBMIT_KEY_PREFIX + culture.getId(), JSONUtil.toJsonStr(culture),
                    REDIS_EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis 提交快照写入失败（不影响主流程）：cultureId={}, err={}", culture.getId(), e.getMessage());
        }
    }

    /** 待审核队列同步（Redis Set）：发布 / 修改重新提交时加入 */
    private void addPendingAudit(Long cultureId) {
        try {
            stringRedisTemplate.opsForSet().add(REDIS_PENDING_AUDIT_KEY, cultureId.toString());
        } catch (Exception e) {
            log.warn("Redis 待审核队列加入失败（不影响主流程）：cultureId={}, err={}", cultureId, e.getMessage());
        }
    }

    /** 待审核队列同步（Redis Set）：审核完成时移除 */
    private void removePendingAudit(Long cultureId) {
        try {
            stringRedisTemplate.opsForSet().remove(REDIS_PENDING_AUDIT_KEY, cultureId.toString());
        } catch (Exception e) {
            log.warn("Redis 待审核队列移除失败（不影响主流程）：cultureId={}, err={}", cultureId, e.getMessage());
        }
    }

    /** 缓存审核记录至 Redis：审核结论、驳回原因、审核人、审核时间（覆盖旧记录，7天过期） */
    private void cacheAuditRecord(IntangibleCulture culture, Integer auditStatus, String remark, Long auditorId) {
        try {
            CultureAuditRecordVO record = new CultureAuditRecordVO();
            record.setCultureId(culture.getId());
            record.setCultureTitle(culture.getTitle());
            record.setAuditStatus(auditStatus);
            record.setRemark(remark);
            record.setAuditorId(auditorId);
            record.setAuditTime(DateUtil.now());
            redisUtil.setEx(REDIS_AUDIT_RECORD_KEY_PREFIX + culture.getId(), JSONUtil.toJsonStr(record),
                    REDIS_EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis 审核记录写入失败（不影响主流程）：cultureId={}, err={}", culture.getId(), e.getMessage());
        }
    }

    /** 读取 Redis 审核记录：不存在 / 过期 / 解析失败时返回 null */
    private CultureAuditRecordVO readAuditRecord(Long cultureId) {
        try {
            String json = redisUtil.get(REDIS_AUDIT_RECORD_KEY_PREFIX + cultureId);
            return (json == null || json.isBlank()) ? null : JSONUtil.toBean(json, CultureAuditRecordVO.class);
        } catch (Exception e) {
            log.warn("Redis 审核记录读取失败：cultureId={}, err={}", cultureId, e.getMessage());
            return null;
        }
    }

    /** 为资料列表回填审核信息（驳回原因 / 审核时间）：待审核资料无审核记录，跳过 */
    private void fillAuditInfo(List<IntangibleCulture> records) {
        if (records == null) {
            return;
        }
        for (IntangibleCulture culture : records) {
            if (culture.getStatus() == null || culture.getStatus() == STATUS_PENDING) {
                continue;
            }
            CultureAuditRecordVO record = readAuditRecord(culture.getId());
            if (record != null) {
                culture.setAuditRemark(record.getRemark());
                culture.setAuditTime(record.getAuditTime());
            }
        }
    }

    // ———————————————— 管理端首页数据统计 ————————————————

    @Override
    public AdminStatsVO adminStats() {
        AdminStatsVO vo = new AdminStatsVO();
        // 1.资料总量与状态分布（0待审核 / 1已发布 / 2已驳回，逻辑删除记录自动排除）
        vo.setCultureTotal(count());
        vo.setPendingCount(lambdaQuery().eq(IntangibleCulture::getStatus, STATUS_PENDING).count());
        vo.setPublishedCount(lambdaQuery().eq(IntangibleCulture::getStatus, STATUS_PUBLISHED).count());
        vo.setRejectedCount(lambdaQuery().eq(IntangibleCulture::getStatus, STATUS_REJECTED).count());
        // 2.总浏览量（SUM(view_count)，无数据返回0）
        List<Object> viewSums = listObjs(new QueryWrapper<IntangibleCulture>().select("IFNULL(SUM(view_count), 0)"));
        vo.setTotalViewCount(viewSums.isEmpty() ? 0L : ((Number) viewSums.get(0)).longValue());
        // 3.近7天发布趋势：按天分组统计后补零组装（含今天，日期升序）
        List<Map<String, Object>> rows = listMaps(new QueryWrapper<IntangibleCulture>()
                .select("DATE_FORMAT(create_time, '%m-%d') AS trend_date", "COUNT(*) AS trend_count")
                .ge("create_time", LocalDate.now().minusDays(6).atStartOfDay())
                .groupBy("DATE_FORMAT(create_time, '%m-%d')"));
        vo.setPublishTrend(StatsUtil.fillLast7Days(StatsUtil.toCountByDate(rows, "trend_date", "trend_count")));
        return vo;
    }

    /** 删除资料时清理 Redis 相关数据（提交快照 / 审核记录 / 待审核队列） */
    private void clearRedisCache(Long cultureId) {
        try {
            redisUtil.delete(REDIS_SUBMIT_KEY_PREFIX + cultureId);
            redisUtil.delete(REDIS_AUDIT_RECORD_KEY_PREFIX + cultureId);
            stringRedisTemplate.opsForSet().remove(REDIS_PENDING_AUDIT_KEY, cultureId.toString());
        } catch (Exception e) {
            log.warn("Redis 审核数据清理失败（不影响主流程）：cultureId={}, err={}", cultureId, e.getMessage());
        }
    }

    // ———————————————— Redis 前台数据缓存读写（异常仅告警，降级查库，不影响主流程） ————————————————

    /** 读取缓存并反序列化：不存在 / 过期 / Redis 异常 / 解析失败均返回 null（降级查库） */
    private <T> T readCache(String key, TypeReference<T> typeRef) {
        try {
            String json = redisUtil.get(key);
            return StringUtils.hasText(json) ? objectMapper.readValue(json, typeRef) : null;
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败（降级查库）：key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 写入缓存（JSON，基础时长 + 随机抖动防雪崩）：失败仅告警，不影响数据库主流程 */
    private void writeCache(String key, Object value, long baseSeconds) {
        try {
            long ttl = baseSeconds + ThreadLocalRandom.current().nextInt(CACHE_JITTER_SECONDS + 1);
            redisUtil.setEx(key, objectMapper.writeValueAsString(value), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 缓存写入失败（不影响主流程）：key={}, err={}", key, e.getMessage());
        }
    }

    /** 写操作后失效前台公开数据缓存：全部分页组合（SCAN 匹配删除）+ 指定资料详情（cultureId 为空仅清分页，排版保存场景） */
    private void clearPublishedCache(Long cultureId) {
        try {
            Set<String> keys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(REDIS_PAGE_CACHE_PREFIX + "*").count(200).build();
            try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
                cursor.forEachRemaining(keys::add);
            }
            if (cultureId != null) {
                keys.add(REDIS_DETAIL_CACHE_PREFIX + cultureId);
            }
            stringRedisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("Redis 前台缓存清理失败（等待过期自动失效）：cultureId={}, err={}", cultureId, e.getMessage());
        }
    }
}
