package com.mic.culture.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.culture.mapper.IntangibleCultureMapper;
import com.mic.culture.mapper.UserLikeCollectMapper;
import com.mic.culture.model.entity.IntangibleCulture;
import com.mic.culture.model.entity.UserLikeCollect;
import com.mic.culture.model.vo.LikeCollectStateVO;
import com.mic.culture.service.UserLikeCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 点赞收藏服务实现：
 * - 有效状态通过 is_deleted 切换，同一（用户+资料+类型）仅一条物理记录，取消后再次点赞恢复原记录，避免唯一键冲突
 * - 仅允许对已发布资料点赞 / 收藏（未发布 / 已删除资料前台不可见）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserLikeCollectServiceImpl extends ServiceImpl<UserLikeCollectMapper, UserLikeCollect>
        implements UserLikeCollectService {

    /** 类型：1点赞 */
    private static final int TYPE_LIKE = 1;

    /** 类型：2收藏 */
    private static final int TYPE_COLLECT = 2;

    /** 资料状态：1已发布（前台公开） */
    private static final int STATUS_PUBLISHED = 1;

    /** 逻辑删除标记：0有效 / 1已取消 */
    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    /** 非遗资料Mapper：校验资料存在且已发布 */
    private final IntangibleCultureMapper intangibleCultureMapper;

    @Override
    public void likeOrCollect(Long userId, Long cultureId, Integer type) {
        checkType(type);
        checkCulturePublished(cultureId);

        UserLikeCollect record = baseMapper.selectIncludingDeleted(userId, cultureId, type);
        if (record != null && Objects.equals(record.getIsDeleted(), NOT_DELETED)) {
            // 幂等：已点赞 / 已收藏，直接返回
            return;
        }
        if (record != null) {
            // 历史取消记录恢复为有效，避免插入触发唯一键 uk_user_culture_type 冲突
            baseMapper.updateDeletedById(record.getId(), NOT_DELETED);
        } else {
            UserLikeCollect create = new UserLikeCollect();
            create.setUserId(userId);
            create.setCultureId(cultureId);
            create.setType(type);
            try {
                save(create);
            } catch (DuplicateKeyException e) {
                // 并发重复提交：已有有效记录，幂等忽略
                log.warn("点赞收藏并发重复提交，已幂等忽略：userId={}, cultureId={}, type={}", userId, cultureId, type);
                return;
            }
        }
        log.info("{}成功：userId={}, cultureId={}", type == TYPE_LIKE ? "点赞" : "收藏", userId, cultureId);
    }

    @Override
    public void cancel(Long userId, Long cultureId, Integer type) {
        checkType(type);

        UserLikeCollect record = baseMapper.selectIncludingDeleted(userId, cultureId, type);
        if (record == null || Objects.equals(record.getIsDeleted(), DELETED)) {
            // 幂等：未点赞 / 未收藏（或已取消），直接返回
            return;
        }
        baseMapper.updateDeletedById(record.getId(), DELETED);
        log.info("取消{}成功：userId={}, cultureId={}", type == TYPE_LIKE ? "点赞" : "收藏", userId, cultureId);
    }

    @Override
    public PageResult<IntangibleCulture> pageMy(Long userId, Integer type, PageDto pageDto) {
        checkType(type);

        Page<IntangibleCulture> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        // 连表查询仍有效的已发布资料（资料被删除 / 未发布时自动排除），分页插件自动分页与统计
        baseMapper.selectMyPage(page, userId, type);
        return PageResult.of(page);
    }

    @Override
    public LikeCollectStateVO getState(Long userId, Long cultureId) {
        List<UserLikeCollect> records = baseMapper.selectActiveByUserAndCulture(userId, cultureId);
        LikeCollectStateVO stateVO = new LikeCollectStateVO();
        stateVO.setLiked(records.stream().anyMatch(record -> record.getType() == TYPE_LIKE));
        stateVO.setCollected(records.stream().anyMatch(record -> record.getType() == TYPE_COLLECT));
        return stateVO;
    }

    /** 校验类型合法：1点赞 / 2收藏（兜底校验，防止内部调用绕开 DTO 注解约束） */
    private void checkType(Integer type) {
        if (type == null || (type != TYPE_LIKE && type != TYPE_COLLECT)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "类型只能为1点赞，2收藏");
        }
    }

    /** 校验资料存在且已发布（未发布 / 已删除资料不可点赞 / 收藏） */
    private void checkCulturePublished(Long cultureId) {
        IntangibleCulture culture = intangibleCultureMapper.selectById(cultureId);
        if (culture == null || !Objects.equals(culture.getStatus(), STATUS_PUBLISHED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "非遗资料不存在或未发布");
        }
    }
}
