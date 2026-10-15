package com.mic.culture.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.NoticeCreateDTO;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.culture.feign.NoticeFeignClient;
import com.mic.culture.mapper.CultureCommentMapper;
import com.mic.culture.mapper.IntangibleCultureMapper;
import com.mic.culture.model.dto.CultureCommentDTO;
import com.mic.culture.model.entity.CultureComment;
import com.mic.culture.model.entity.IntangibleCulture;
import com.mic.culture.model.vo.CultureCommentVO;
import com.mic.culture.service.CultureCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评论服务实现：
 * - 二级结构：parent_id 统一指向所属顶级评论；回复回复时后端自动归一（顶级id + 被回复人）
 * - 仅已发布资料可发表评论 / 回复（未发布 / 已删除资料前台不可见）
 * - 删除权限：评论作者本人 或 资料发布者；删除顶级评论时连带逻辑删除其回复
 * - 昵称 / 头像 / 被回复人昵称通过 LEFT JOIN sys_user（同库）回填，作者标注由发布者ID比对回填
 * - 发表评论 / 回复后经 Feign 远程调用通知服务（mic-notice）创建通知：评论文章通知作者、回复评论通知被回复人
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CultureCommentServiceImpl extends ServiceImpl<CultureCommentMapper, CultureComment>
        implements CultureCommentService {

    /** 资料状态：1已发布（前台公开） */
    private static final int STATUS_PUBLISHED = 1;

    /** 顶级评论父ID */
    private static final long TOP_PARENT_ID = 0L;

    /** 通知类型：1文章评论 2文章回复（与 notification 表 notify_type 定义一致） */
    private static final int NOTIFY_TYPE_COMMENT = 1;
    private static final int NOTIFY_TYPE_REPLY = 2;

    /** 通知标题最大长度（notification.title varchar(128)） */
    private static final int NOTIFY_TITLE_MAX = 128;

    /** 非遗资料Mapper：校验资料并回填发布者（作者标注 / 删除权限判定） */
    private final IntangibleCultureMapper intangibleCultureMapper;

    /** 通知服务Feign客户端：评论 / 回复成功后创建通知（远程调用失败不影响评论主流程） */
    private final NoticeFeignClient noticeFeignClient;

    @Override
    public PageResult<CultureCommentVO> pageByCulture(Long cultureId, PageDto pageDto) {
        Page<CultureCommentVO> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        baseMapper.selectTopPage(page, cultureId);

        List<CultureCommentVO> tops = page.getRecords();
        if (!tops.isEmpty()) {
            // 批量查询二级回复并按所属顶级评论归拢（避免逐条查询）
            List<Long> topIds = tops.stream().map(CultureCommentVO::getId).toList();
            Map<Long, List<CultureCommentVO>> replyMap = baseMapper.selectRepliesByParentIds(topIds).stream()
                    .collect(Collectors.groupingBy(CultureCommentVO::getParentId));
            tops.forEach(top -> top.setReplies(replyMap.getOrDefault(top.getId(), List.of())));
        }

        // 作者标注：评论者 / 回复者为资料发布者时回填 isAuthor（资料不存在时统一为 false）
        IntangibleCulture culture = intangibleCultureMapper.selectById(cultureId);
        Long publisherId = culture == null ? null : culture.getPublisherId();
        tops.forEach(top -> {
            top.setIsAuthor(Objects.equals(top.getUserId(), publisherId));
            top.getReplies().forEach(reply -> reply.setIsAuthor(Objects.equals(reply.getUserId(), publisherId)));
        });
        return PageResult.of(page);
    }

    @Override
    public CultureCommentVO publish(Long userId, CultureCommentDTO dto) {
        IntangibleCulture culture = intangibleCultureMapper.selectById(dto.getCultureId());
        if (culture == null || !Objects.equals(culture.getStatus(), STATUS_PUBLISHED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "非遗资料不存在或未发布");
        }

        // 归一化被回复评论：回复顶级 →（顶级id, 顶级作者）；回复二级回复 →（所属顶级id, 被回复人）
        long parentId = TOP_PARENT_ID;
        Long replyUserId = null;
        if (dto.getParentId() != null && dto.getParentId() > TOP_PARENT_ID) {
            CultureComment parent = getById(dto.getParentId());
            if (parent == null || !Objects.equals(parent.getCultureId(), dto.getCultureId())) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "被回复的评论不存在");
            }
            Long topId = parent.getParentId();
            parentId = (topId != null && topId > TOP_PARENT_ID) ? topId : parent.getId();
            replyUserId = parent.getUserId();
        }

        CultureComment comment = new CultureComment();
        comment.setCultureId(dto.getCultureId());
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setReplyUserId(replyUserId);
        comment.setCommentContent(dto.getCommentContent().trim());
        save(comment);

        CultureCommentVO vo = baseMapper.selectVOById(comment.getId());
        vo.setIsAuthor(Objects.equals(userId, culture.getPublisherId()));

        // 评论 / 回复成功后触发通知（Feign 远程调用通知服务；失败不影响评论主流程）
        sendCommentNotice(userId, vo, culture, replyUserId);

        log.info("评论发布成功：id={}, cultureId={}, userId={}, parentId={}",
                comment.getId(), dto.getCultureId(), userId, parentId);
        return vo;
    }

    @Override
    public void remove(Long userId, Long id) {
        CultureComment comment = getById(id);
        if (comment == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "评论不存在或已删除");
        }
        // 权限：评论作者本人 或 资料发布者
        IntangibleCulture culture = intangibleCultureMapper.selectById(comment.getCultureId());
        boolean isCommentAuthor = Objects.equals(comment.getUserId(), userId);
        boolean isCultureAuthor = culture != null && Objects.equals(culture.getPublisherId(), userId);
        if (!isCommentAuthor && !isCultureAuthor) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅评论作者或资料发布者可删除该评论");
        }
        // 顶级评论：连带逻辑删除其全部二级回复
        if (Objects.equals(comment.getParentId(), TOP_PARENT_ID)) {
            baseMapper.updateDeletedByParentId(comment.getId());
        }
        removeById(id);
        log.info("评论删除成功：id={}, operatorId={}（评论作者={}, 资料作者={}）",
                id, userId, isCommentAuthor, isCultureAuthor);
    }

    /**
     * 评论 / 回复成功后触发通知（Feign 远程调用 mic-notice 创建通知）：
     * - 评论文章：通知文章作者（1文章评论）
     * - 回复评论：通知被回复人（2文章回复）
     * 说明：自己评论自己的文章 / 回复自己的评论不产生通知；
     *      通知失败不影响评论主流程，仅记录告警日志（评论已发布成功）
     */
    private void sendCommentNotice(Long userId, CultureCommentVO vo, IntangibleCulture culture,
                                   Long replyUserId) {
        Long receiveUserId = replyUserId != null ? replyUserId : culture.getPublisherId();
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
            notice.setNotifyType(NOTIFY_TYPE_COMMENT);
            notice.setTitle(truncateNoticeTitle(nickname + " 评论了你的文章《" + culture.getTitle() + "》"));
        }
        notice.setContent(vo.getCommentContent());
        notice.setBizType("culture");
        notice.setBizId(String.valueOf(culture.getId()));

        try {
            Result<Void> result = noticeFeignClient.create(notice);
            if (result == null || !ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())) {
                log.warn("评论通知发送失败：receiveUserId={}, result={}", receiveUserId, result);
            }
        } catch (Exception e) {
            log.warn("评论通知发送异常（评论已发布成功，不影响业务）：receiveUserId={}, cultureId={}, error={}",
                    receiveUserId, culture.getId(), e.getMessage());
        }
    }

    /** 通知标题截断：标题为昵称 + 文章标题拼接，文章标题最长120字符可能超出 notification.title 的128上限 */
    private String truncateNoticeTitle(String title) {
        return title.length() <= NOTIFY_TITLE_MAX
                ? title
                : title.substring(0, NOTIFY_TITLE_MAX - 3) + "...";
    }
}
