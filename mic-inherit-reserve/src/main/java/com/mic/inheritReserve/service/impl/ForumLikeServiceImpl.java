package com.mic.inheritReserve.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.inheritReserve.mapper.ForumLikeMapper;
import com.mic.inheritReserve.mapper.ForumPostMapper;
import com.mic.inheritReserve.mapper.ForumReplyLikeMapper;
import com.mic.inheritReserve.mapper.ForumReplyMapper;
import com.mic.inheritReserve.model.entity.ForumLike;
import com.mic.inheritReserve.model.entity.ForumPost;
import com.mic.inheritReserve.model.entity.ForumReply;
import com.mic.inheritReserve.model.entity.ForumReplyLike;
import com.mic.inheritReserve.service.ForumLikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 论坛点赞服务实现（帖子点赞 + 评论点赞）：
 * 复用 user_like_collect 的点赞模式：同一（用户+目标）仅保留一条物理记录，is_deleted 切换有效状态
 * （0有效 / 1取消），取消后再次点赞恢复原记录，重复 / 并发点赞由唯一键兜底幂等；
 * 点赞对象校验：帖子须已通过审核（未通过帖子前台不可见），评论须存在且所属帖子已通过
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumLikeServiceImpl extends ServiceImpl<ForumLikeMapper, ForumLike> implements ForumLikeService {

    /** 逻辑删除标记：0有效点赞 */
    private static final int NOT_DELETED = 0;

    /** 逻辑删除标记：1已取消点赞 */
    private static final int DELETED = 1;

    /** 审核状态：1已通过（仅已通过帖子的点赞有效） */
    private static final int AUDIT_PASSED = 1;

    private final ForumPostMapper forumPostMapper;
    private final ForumReplyMapper forumReplyMapper;
    private final ForumReplyLikeMapper forumReplyLikeMapper;

    @Override
    public void likePost(Long postId, Long userId) {
        checkPostPublished(postId);
        // 含已取消记录查询：取消后再次点赞恢复原记录，规避唯一键冲突
        ForumLike record = baseMapper.selectIncludingDeleted(userId, postId);
        if (record != null && Objects.equals(record.getIsDeleted(), NOT_DELETED)) {
            // 已点赞：幂等返回
            return;
        }
        if (record != null) {
            baseMapper.updateDeletedById(record.getId(), NOT_DELETED);
        } else {
            ForumLike create = new ForumLike();
            create.setUserId(userId);
            create.setPostId(postId);
            try {
                save(create);
            } catch (DuplicateKeyException e) {
                // 并发重复点赞：唯一键兜底，幂等忽略
                return;
            }
        }
        log.info("论坛帖子点赞成功：postId={}, userId={}", postId, userId);
    }

    @Override
    public void cancelPostLike(Long postId, Long userId) {
        ForumLike record = baseMapper.selectIncludingDeleted(userId, postId);
        if (record == null || Objects.equals(record.getIsDeleted(), DELETED)) {
            // 未点赞或已取消：幂等返回
            return;
        }
        baseMapper.updateDeletedById(record.getId(), DELETED);
        log.info("论坛帖子取消点赞成功：postId={}, userId={}", postId, userId);
    }

    @Override
    public Boolean getPostLikeState(Long postId, Long userId) {
        // 未登录（游客）：统一视为未点赞
        if (userId == null) {
            return false;
        }
        return baseMapper.selectActiveByUserAndPost(userId, postId) != null;
    }

    @Override
    public PageResult<ForumPost> pageMyLiked(PageDto pageDto, Long userId) {
        // 帖子被删除 / 未通过审核时自动排除（SQL INNER JOIN 过滤）
        Page<ForumPost> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        IPage<ForumPost> result = baseMapper.selectMyLikedPage(page, userId);
        return PageResult.of(result);
    }

    @Override
    public void likeReply(Long replyId, Long userId) {
        ForumReply reply = forumReplyMapper.selectById(replyId);
        if (reply == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "评论不存在或已删除");
        }
        // 仅已通过帖子下的评论可点赞（与发表评论口径一致）
        checkPostPublished(reply.getPostId());

        ForumReplyLike record = forumReplyLikeMapper.selectIncludingDeleted(userId, replyId);
        if (record != null && Objects.equals(record.getIsDeleted(), NOT_DELETED)) {
            // 已点赞：幂等返回
            return;
        }
        if (record != null) {
            forumReplyLikeMapper.updateDeletedById(record.getId(), NOT_DELETED);
        } else {
            ForumReplyLike create = new ForumReplyLike();
            create.setUserId(userId);
            create.setReplyId(replyId);
            try {
                forumReplyLikeMapper.insert(create);
            } catch (DuplicateKeyException e) {
                // 并发重复点赞：唯一键兜底，幂等忽略
                return;
            }
        }
        log.info("论坛评论点赞成功：replyId={}, userId={}", replyId, userId);
    }

    @Override
    public void cancelReplyLike(Long replyId, Long userId) {
        ForumReplyLike record = forumReplyLikeMapper.selectIncludingDeleted(userId, replyId);
        if (record == null || Objects.equals(record.getIsDeleted(), DELETED)) {
            // 未点赞或已取消：幂等返回
            return;
        }
        forumReplyLikeMapper.updateDeletedById(record.getId(), DELETED);
        log.info("论坛评论取消点赞成功：replyId={}, userId={}", replyId, userId);
    }

    @Override
    public List<Long> listLikedReplyIds(Long userId, List<Long> replyIds) {
        // 未登录 / 空集合：直接返回（同时避免 SQL IN () 语法错误）
        if (userId == null || replyIds == null || replyIds.isEmpty()) {
            return List.of();
        }
        return forumReplyLikeMapper.selectLikedReplyIds(userId, replyIds);
    }

    /** 校验帖子已通过审核（未通过 / 已删除帖子前台不可见，不允许点赞） */
    private void checkPostPublished(Long postId) {
        ForumPost post = forumPostMapper.selectBriefById(postId);
        if (post == null || !Objects.equals(post.getAuditStatus(), AUDIT_PASSED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "帖子不存在或已删除");
        }
    }
}
