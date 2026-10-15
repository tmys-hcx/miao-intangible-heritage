package com.mic.inheritReserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.NoticeCreateDTO;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.inheritReserve.feign.NoticeFeignClient;
import com.mic.inheritReserve.mapper.ForumPostMapper;
import com.mic.inheritReserve.mapper.ForumReplyMapper;
import com.mic.inheritReserve.mapper.ForumTopicMapper;
import com.mic.inheritReserve.model.dto.ForumPublishDTO;
import com.mic.inheritReserve.model.dto.ForumReplyDTO;
import com.mic.inheritReserve.model.entity.ForumPost;
import com.mic.inheritReserve.model.entity.ForumReply;
import com.mic.inheritReserve.model.entity.ForumTopic;
import com.mic.inheritReserve.model.vo.ForumReplyVO;
import com.mic.inheritReserve.service.ForumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 论坛服务实现：帖子发布 / 编辑 / 删除（仅作者本人）+ 公开分页 / 详情 + 话题列表
 * + 两级回复（分页 / 发表 / 删除，parent_id 归一化，结构对齐 culture_comment）
 * 审核制：发帖 / 编辑后进入待审核（audit_status=0），管理员审核通过（1）后前台展示；
 * 未通过帖子仅作者本人可预览（个人中心「我发布的帖子」入口，含驳回原因）；
 * 话题使用次数随发布 / 编辑（换话题）/ 删除同步增减，供「#」浮层按热度展示
 * 回复帖子 / 回复评论成功后经 Feign 远程调用通知服务（mic-notice）创建通知：
 * 回复帖子通知作者（3论坛帖子回复）、回复评论通知被回复人（4论坛评论回复）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForumServiceImpl extends ServiceImpl<ForumPostMapper, ForumPost> implements ForumService {

    /** 审核状态：0待审核（发帖 / 编辑后默认，前台不可见） */
    private static final int AUDIT_PENDING = 0;

    /** 审核状态：1已通过（公开分页 / 详情对所有人展示该状态） */
    private static final int AUDIT_PASSED = 1;

    /** 审核状态：2已驳回（作者可查看原因并修改重新提交） */
    private static final int AUDIT_REJECTED = 2;

    /** 顶级回复父ID */
    private static final long TOP_PARENT_ID = 0L;

    /** 通知类型：3论坛帖子回复 4论坛评论回复（与 notification 表 notify_type 定义一致） */
    private static final int NOTIFY_TYPE_POST_REPLY = 3;
    private static final int NOTIFY_TYPE_REPLY = 4;

    /** 通知标题最大长度（notification.title varchar(128)） */
    private static final int NOTIFY_TITLE_MAX = 128;

    private final ForumTopicMapper forumTopicMapper;
    private final ForumReplyMapper forumReplyMapper;

    /** 通知服务Feign客户端：回复成功后创建通知（远程调用失败不影响回复主流程） */
    private final NoticeFeignClient noticeFeignClient;

    @Override
    public PageResult<ForumPost> pagePublic(PageDto pageDto, String keyword) {
        // 逻辑删除与审核状态已在 SQL 中手动过滤（自定义 SQL 不走 @TableLogic）；keyword 为论坛搜索关键词
        Page<ForumPost> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        IPage<ForumPost> result = baseMapper.selectPublicPage(page, keyword);
        return PageResult.of(result);
    }

    @Override
    public List<ForumTopic> listTopics() {
        return forumTopicMapper.selectHotList();
    }

    @Override
    public Long publishPost(ForumPublishDTO dto, Long userId) {
        checkTopicExists(dto.getTopicId());
        ForumPost post = new ForumPost();
        post.setPublisherId(userId);
        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setTopicId(dto.getTopicId());
        post.setImgUrl(dto.getImgUrl());
        post.setVideoUrl(dto.getVideoUrl());
        // 提交后进入待审核（audit_status=0），管理员审核通过后前台展示
        post.setAuditStatus(AUDIT_PENDING);
        save(post);
        // 选用话题：使用次数 +1（热度排序）
        if (dto.getTopicId() != null) {
            forumTopicMapper.updateUseCount(dto.getTopicId(), 1);
        }
        log.info("发布论坛帖子：postId={}, userId={}, topicId={}", post.getId(), userId, dto.getTopicId());
        return post.getId();
    }

    @Override
    public void updatePost(Long id, ForumPublishDTO dto, Long userId) {
        ForumPost post = getOwnedPost(id, userId);
        checkTopicExists(dto.getTopicId());
        // 话题变更（含取消话题）：旧话题 -1、新话题 +1，保持使用次数准确
        if (!Objects.equals(post.getTopicId(), dto.getTopicId())) {
            if (post.getTopicId() != null) {
                forumTopicMapper.updateUseCount(post.getTopicId(), -1);
            }
            if (dto.getTopicId() != null) {
                forumTopicMapper.updateUseCount(dto.getTopicId(), 1);
            }
        }
        ForumPost update = new ForumPost();
        update.setTitle(dto.getTitle());
        update.setContent(dto.getContent());
        update.setTopicId(dto.getTopicId());
        update.setImgUrl(dto.getImgUrl());
        update.setVideoUrl(dto.getVideoUrl());
        // 编辑后重新进入待审核，管理员重审通过后展示；同时清空上一轮审核结果，避免“先审后改”绕过审核
        update.setAuditStatus(AUDIT_PENDING);
        LambdaUpdateWrapper<ForumPost> wrapper = new LambdaUpdateWrapper<ForumPost>()
                .eq(ForumPost::getId, id)
                .set(ForumPost::getAuditRemark, null)
                .set(ForumPost::getAuditTime, null)
                .set(ForumPost::getAuditorId, null);
        update(update, wrapper);
        log.info("编辑论坛帖子（重新进入待审核）：postId={}, userId={}, topicId={}", id, userId, dto.getTopicId());
    }

    @Override
    public void removePost(Long id, Long userId) {
        ForumPost post = getOwnedPost(id, userId);
        removeById(id);
        // 删除后对应话题使用次数 -1
        if (post.getTopicId() != null) {
            forumTopicMapper.updateUseCount(post.getTopicId(), -1);
        }
        log.info("删除论坛帖子：postId={}, userId={}", id, userId);
    }

    @Override
    public ForumPost getDetail(Long id, Long userId) {
        ForumPost post = baseMapper.selectDetailById(id);
        if (post == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "帖子不存在或已删除");
        }
        // 未通过审核（待审核 / 已驳回）的帖子仅作者本人可预览，其他访客一律视为不存在
        boolean passed = Objects.equals(post.getAuditStatus(), AUDIT_PASSED);
        boolean isAuthor = userId != null && Objects.equals(post.getPublisherId(), userId);
        if (!passed && !isAuthor) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "帖子不存在或已删除");
        }
        return post;
    }

    @Override
    public PageResult<ForumPost> pageMyPublish(PageDto pageDto, Long userId) {
        // 本人全部审核状态（含待审核 / 已驳回与驳回原因），归属与逻辑删除在 SQL 中手动过滤
        Page<ForumPost> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        IPage<ForumPost> result = baseMapper.selectMyPage(page, userId);
        return PageResult.of(result);
    }

    @Override
    public PageResult<ForumPost> pageAdmin(PageDto pageDto, Integer auditStatus, String keyword) {
        // 管理端：全部状态（含未通过），可按审核状态 / 标题关键词筛选
        Page<ForumPost> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        IPage<ForumPost> result = baseMapper.selectAdminPage(page, auditStatus, keyword);
        return PageResult.of(result);
    }

    @Override
    public void auditPost(Long id, Integer auditStatus, String remark, Long auditorId) {
        ForumPost post = baseMapper.selectBriefById(id);
        if (post == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "帖子不存在或已删除");
        }
        if (Objects.equals(auditStatus, AUDIT_REJECTED) && !StringUtils.hasText(remark)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "驳回时必须填写驳回原因");
        }
        ForumPost update = new ForumPost();
        update.setId(id);
        update.setAuditStatus(auditStatus);
        // 驳回时记录不合格原因；通过时置空备注（仅保留最新审核结论相关备注）
        update.setAuditRemark(Objects.equals(auditStatus, AUDIT_REJECTED) ? remark : null);
        update.setAuditTime(LocalDateTime.now());
        update.setAuditorId(auditorId);
        updateById(update);
        log.info("论坛帖子审核：postId={}, auditStatus={}, auditorId={}", id, auditStatus, auditorId);
    }

    @Override
    public PageResult<ForumReplyVO> pageReplies(Long postId, PageDto pageDto) {
        // 仅已通过帖子可查看评论列表（未通过帖子前台不可见，作者预览模式不展示评论区）
        ForumPost post = baseMapper.selectBriefById(postId);
        if (post == null || !Objects.equals(post.getAuditStatus(), AUDIT_PASSED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "帖子不存在或已删除");
        }

        Page<ForumReplyVO> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        forumReplyMapper.selectTopPage(page, postId);

        List<ForumReplyVO> tops = page.getRecords();
        if (!tops.isEmpty()) {
            // 批量查询二级回复并按所属顶级回复归拢（避免逐条查询）
            List<Long> topIds = tops.stream().map(ForumReplyVO::getId).toList();
            Map<Long, List<ForumReplyVO>> replyMap = forumReplyMapper.selectRepliesByParentIds(topIds).stream()
                    .collect(Collectors.groupingBy(ForumReplyVO::getParentId));
            tops.forEach(top -> top.setReplies(replyMap.getOrDefault(top.getId(), List.of())));
        }

        // 作者标注：评论者为帖子作者时回填 isAuthor（帖子已在上方校验查得，直接取发布者）
        Long publisherId = post.getPublisherId();
        tops.forEach(top -> {
            top.setIsAuthor(Objects.equals(top.getUserId(), publisherId));
            top.getReplies().forEach(reply -> reply.setIsAuthor(Objects.equals(reply.getUserId(), publisherId)));
        });
        return PageResult.of(page);
    }

    @Override
    public ForumReplyVO publishReply(ForumReplyDTO dto, Long userId) {
        ForumPost post = baseMapper.selectDetailById(dto.getPostId());
        // 仅已通过帖子可被回复（未通过帖子前台不可见，作者预览模式不开放回复）
        if (post == null || !Objects.equals(post.getAuditStatus(), AUDIT_PASSED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "帖子不存在或已删除");
        }

        // 归一化被回复目标：回复顶级 →（顶级id, 顶级作者）；回复二级 →（所属顶级id, 被回复人）
        long parentId = TOP_PARENT_ID;
        Long replyUserId = null;
        if (dto.getParentId() != null && dto.getParentId() > TOP_PARENT_ID) {
            ForumReply parent = forumReplyMapper.selectById(dto.getParentId());
            if (parent == null || !Objects.equals(parent.getPostId(), dto.getPostId())) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "该评论不存在或已删除");
            }
            Long topId = parent.getParentId();
            parentId = (topId != null && topId > TOP_PARENT_ID) ? topId : parent.getId();
            replyUserId = parent.getUserId();
        }

        ForumReply reply = new ForumReply();
        reply.setPostId(dto.getPostId());
        reply.setUserId(userId);
        reply.setParentId(parentId);
        reply.setReplyUserId(replyUserId);
        reply.setReplyContent(dto.getReplyContent().trim());
        forumReplyMapper.insert(reply);

        ForumReplyVO vo = forumReplyMapper.selectVOById(reply.getId());
        vo.setIsAuthor(Objects.equals(userId, post.getPublisherId()));

        // 回复成功后触发通知（Feign 远程调用通知服务；失败不影响回复主流程）
        sendReplyNotice(userId, vo, post, replyUserId);

        log.info("论坛回复发布成功：id={}, postId={}, userId={}, parentId={}",
                reply.getId(), dto.getPostId(), userId, parentId);
        return vo;
    }

    @Override
    public void removeReply(Long id, Long userId) {
        ForumReply reply = forumReplyMapper.selectById(id);
        if (reply == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "评论不存在或已删除");
        }
        // 权限：回复作者本人 或 帖子作者（作者可管理自己帖子下的回复）
        ForumPost post = baseMapper.selectBriefById(reply.getPostId());
        boolean isReplyAuthor = Objects.equals(reply.getUserId(), userId);
        boolean isPostAuthor = post != null && Objects.equals(post.getPublisherId(), userId);
        if (!isReplyAuthor && !isPostAuthor) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅评论作者或帖子作者可删除该评论");
        }
        // 顶级回复：连带逻辑删除其全部二级回复
        if (Objects.equals(reply.getParentId(), TOP_PARENT_ID)) {
            forumReplyMapper.updateDeletedByParentId(reply.getId());
        }
        forumReplyMapper.deleteById(id);
        log.info("论坛回复删除成功：id={}, operatorId={}（回复作者={}, 帖子作者={}）",
                id, userId, isReplyAuthor, isPostAuthor);
    }

    /** 校验话题存在（topicId 为空视为无话题；不存在抛 400） */
    private void checkTopicExists(Long topicId) {
        if (topicId == null) {
            return;
        }
        if (forumTopicMapper.selectById(topicId) == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "话题不存在或已删除");
        }
    }

    /** 查询帖子并校验归属（仅作者本人可编辑 / 删除；不存在或非本人抛业务异常；简要查询避免全列依赖） */
    private ForumPost getOwnedPost(Long id, Long userId) {
        ForumPost post = baseMapper.selectBriefById(id);
        if (post == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "帖子不存在或已删除");
        }
        if (!Objects.equals(post.getPublisherId(), userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅作者本人可操作该帖子");
        }
        return post;
    }

    /**
     * 回复帖子 / 回复评论成功后触发通知（Feign 远程调用 mic-notice 创建通知）：
     * - 回复帖子：通知帖子作者（3论坛帖子回复）
     * - 回复评论：通知被回复人（4论坛评论回复）
     * 说明：自己回复自己的帖子 / 评论不产生通知；
     *      通知失败不影响回复主流程，仅记录告警日志（回复已发布成功）
     */
    private void sendReplyNotice(Long userId, ForumReplyVO vo, ForumPost post, Long replyUserId) {
        Long receiveUserId = replyUserId != null ? replyUserId : post.getPublisherId();
        if (receiveUserId == null || Objects.equals(receiveUserId, userId)) {
            return;
        }

        String nickname = vo.getNickname() == null ? "用户" : vo.getNickname();
        NoticeCreateDTO notice = new NoticeCreateDTO();
        notice.setReceiveUserId(receiveUserId);
        notice.setSendUserId(userId);
        if (replyUserId != null) {
            notice.setNotifyType(NOTIFY_TYPE_REPLY);
            notice.setTitle(nickname + " 回复了你的评论");
        } else {
            notice.setNotifyType(NOTIFY_TYPE_POST_REPLY);
            notice.setTitle(truncateNoticeTitle(nickname + " 回复了你的帖子《" + post.getTitle() + "》"));
        }
        notice.setContent(vo.getReplyContent());
        notice.setBizType("forum");
        notice.setBizId(String.valueOf(post.getId()));

        try {
            Result<Void> result = noticeFeignClient.create(notice);
            if (result == null || !ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
                log.warn("论坛回复通知发送失败：receiveUserId={}, result={}", receiveUserId, result);
            }
        } catch (Exception e) {
            log.warn("论坛回复通知发送异常（回复已发布成功，不影响业务）：receiveUserId={}, postId={}, error={}",
                    receiveUserId, post.getId(), e.getMessage());
        }
    }

    /** 通知标题截断：标题为昵称 + 帖子标题拼接，帖子标题可能超出 notification.title 的128上限 */
    private String truncateNoticeTitle(String title) {
        return title.length() <= NOTIFY_TITLE_MAX
                ? title
                : title.substring(0, NOTIFY_TITLE_MAX - 3) + "...";
    }
}
