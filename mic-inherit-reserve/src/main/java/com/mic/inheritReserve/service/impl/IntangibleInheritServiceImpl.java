package com.mic.inheritReserve.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.model.vo.InheritSearchVO;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.inheritReserve.mapper.ForumPostMapper;
import com.mic.inheritReserve.mapper.ForumReplyMapper;
import com.mic.inheritReserve.mapper.IntangibleInheritMapper;
import com.mic.inheritReserve.mapper.IntangibleInterviewMapper;
import com.mic.inheritReserve.mapper.IntangibleKnowledgeMapper;
import com.mic.inheritReserve.mapper.IntangibleVideoMapper;
import com.mic.inheritReserve.mapper.UserWatchRecordMapper;
import com.mic.inheritReserve.model.dto.InheritSaveDTO;
import com.mic.inheritReserve.model.dto.VideoPublishDTO;
import com.mic.inheritReserve.model.entity.ForumPost;
import com.mic.inheritReserve.model.entity.IntangibleInherit;
import com.mic.inheritReserve.model.entity.IntangibleInterview;
import com.mic.inheritReserve.model.entity.IntangibleKnowledge;
import com.mic.inheritReserve.model.entity.IntangibleVideo;
import com.mic.inheritReserve.model.vo.AdminStatsVO;
import com.mic.inheritReserve.model.vo.MyWatchVO;
import com.mic.inheritReserve.service.IntangibleInheritService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 非遗传承信息服务实现
 * 说明：视频学习 / 访谈资料 / 知识窗口已按业务拆分至 intangible_video / intangible_interview / intangible_knowledge
 * 三张表（拆分脚本 upgrade-split-inherit.sql）；管理端维护（分页 / 新增 / 编辑 / 删除）与视频审核均已基于三张新表。
 * 个人中心：「我发布的视频」基于 intangible_video 按发布人过滤；「我学习的视频」基于 user_watch_record
 * （视频完整播放结束后幂等上报，重复完整观看仅刷新最近观看时间）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntangibleInheritServiceImpl extends ServiceImpl<IntangibleInheritMapper, IntangibleInherit>
        implements IntangibleInheritService {

    /** 审核状态：0待审核 1已通过（前台仅展示已通过信息） 2已驳回 */
    private static final int AUDIT_PENDING = 0;
    private static final int AUDIT_PASSED = 1;
    private static final int AUDIT_REJECTED = 2;

    /** 信息类型：1视频学习（非遗人发布 + 管理员审核）；3访谈资料 / 4知识窗口（管理员直发，无审核环节） */
    private static final int INFO_TYPE_VIDEO = 1;
    private static final int INFO_TYPE_INTERVIEW = 3;
    private static final int INFO_TYPE_KNOWLEDGE = 4;

    /** 角色：1非遗人（可发布学习视频） */
    private static final int ROLE_INHERITOR = 1;

    /** 资料检索（Agent）：每类资料默认返回条数 / 条数上限 / 单条片段最大字符数 */
    private static final int SEARCH_DEFAULT_LIMIT = 3;
    private static final int SEARCH_MAX_LIMIT = 10;
    private static final int SEARCH_SNIPPET_MAX = 300;

    /** 富文本正文检索用：HTML 标签剥离正则 + 连续空白压缩正则（检索片段转纯文本） */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final IntangibleVideoMapper intangibleVideoMapper;
    private final IntangibleInterviewMapper intangibleInterviewMapper;
    private final IntangibleKnowledgeMapper intangibleKnowledgeMapper;
    private final UserWatchRecordMapper userWatchRecordMapper;

    /** 论坛帖子 / 回复 Mapper：管理端首页统计论坛数据（逻辑删除由 @TableLogic 自动过滤） */
    private final ForumPostMapper forumPostMapper;
    private final ForumReplyMapper forumReplyMapper;

    @Override
    public PageResult<?> pagePublic(PageDto pageDto, Integer infoType) {
        // 逻辑删除条件由 @TableLogic 自动拼接，无需手动过滤 is_deleted
        if (Objects.equals(INFO_TYPE_INTERVIEW, infoType)) {
            Page<IntangibleInterview> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
            LambdaQueryWrapper<IntangibleInterview> wrapper = new LambdaQueryWrapper<IntangibleInterview>()
                    .orderByDesc(IntangibleInterview::getCreateTime)
                    .orderByDesc(IntangibleInterview::getId);
            return PageResult.of(intangibleInterviewMapper.selectPage(page, wrapper));
        }
        if (Objects.equals(INFO_TYPE_KNOWLEDGE, infoType)) {
            Page<IntangibleKnowledge> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
            LambdaQueryWrapper<IntangibleKnowledge> wrapper = new LambdaQueryWrapper<IntangibleKnowledge>()
                    .orderByDesc(IntangibleKnowledge::getCreateTime)
                    .orderByDesc(IntangibleKnowledge::getId);
            return PageResult.of(intangibleKnowledgeMapper.selectPage(page, wrapper));
        }
        // 视频学习（默认）：仅展示管理员审核通过的视频
        Page<IntangibleVideo> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        LambdaQueryWrapper<IntangibleVideo> wrapper = new LambdaQueryWrapper<IntangibleVideo>()
                .eq(IntangibleVideo::getAuditStatus, AUDIT_PASSED)
                .orderByDesc(IntangibleVideo::getCreateTime)
                .orderByDesc(IntangibleVideo::getId);
        return PageResult.of(intangibleVideoMapper.selectPage(page, wrapper));
    }

    @Override
    public Object getPublicDetail(Long id, Integer type) {
        if (type != null) {
            return getPublicDetailByType(id, type);
        }
        // 未携带类型（历史链接）：依次在视频学习 / 访谈资料 / 知识窗口中查找
        IntangibleVideo video = intangibleVideoMapper.selectById(id);
        if (video != null && Objects.equals(video.getAuditStatus(), AUDIT_PASSED)) {
            return video;
        }
        IntangibleInterview interview = intangibleInterviewMapper.selectById(id);
        if (interview != null) {
            return interview;
        }
        IntangibleKnowledge knowledge = intangibleKnowledgeMapper.selectById(id);
        if (knowledge != null) {
            return knowledge;
        }
        throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "传承信息不存在或未发布");
    }

    /** 按信息类型精确查询详情：selectById 由 @TableLogic 自动过滤已删除；未审核视频与不存在统一按 400 处理 */
    private Object getPublicDetailByType(Long id, Integer type) {
        if (Objects.equals(INFO_TYPE_VIDEO, type)) {
            IntangibleVideo video = intangibleVideoMapper.selectById(id);
            if (video != null && Objects.equals(video.getAuditStatus(), AUDIT_PASSED)) {
                return video;
            }
        } else if (Objects.equals(INFO_TYPE_INTERVIEW, type)) {
            IntangibleInterview interview = intangibleInterviewMapper.selectById(id);
            if (interview != null) {
                return interview;
            }
        } else if (Objects.equals(INFO_TYPE_KNOWLEDGE, type)) {
            IntangibleKnowledge knowledge = intangibleKnowledgeMapper.selectById(id);
            if (knowledge != null) {
                return knowledge;
            }
        }
        throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "传承信息不存在或未发布");
    }

    @Override
    public PageResult<?> pageAdmin(PageDto pageDto, Integer infoType, String keyword, Integer auditStatus) {
        // 按信息类型分发三张拆分局表；自定义 SQL 不过 @TableLogic，is_deleted 已在 SQL 中手动过滤
        if (Objects.equals(INFO_TYPE_INTERVIEW, infoType)) {
            Page<IntangibleInterview> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
            IPage<IntangibleInterview> result = intangibleInterviewMapper.selectAdminPage(page, keyword);
            // 回填瞬态信息类型：前端据此外显分类标签与编辑回填
            result.getRecords().forEach(item -> item.setInfoType(INFO_TYPE_INTERVIEW));
            return PageResult.of(result);
        }
        if (Objects.equals(INFO_TYPE_KNOWLEDGE, infoType)) {
            Page<IntangibleKnowledge> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
            IPage<IntangibleKnowledge> result = intangibleKnowledgeMapper.selectAdminPage(page, keyword);
            result.getRecords().forEach(item -> item.setInfoType(INFO_TYPE_KNOWLEDGE));
            return PageResult.of(result);
        }
        // 学习视频审核（默认）：支持审核状态筛选；发布人账号由 SQL LEFT JOIN sys_user 填充
        Page<IntangibleVideo> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        IPage<IntangibleVideo> result = intangibleVideoMapper.selectAdminPage(page, keyword, auditStatus);
        result.getRecords().forEach(item -> item.setInfoType(INFO_TYPE_VIDEO));
        return PageResult.of(result);
    }

    @Override
    public Long saveInherit(InheritSaveDTO dto, Long userId) {
        // 管理端仅维护访谈资料 / 知识窗口（直发无审核）；视频学习由非遗人发布 + 管理员审核
        if (Objects.equals(INFO_TYPE_INTERVIEW, dto.getInfoType())) {
            IntangibleInterview interview = new IntangibleInterview();
            interview.setPublisherId(userId);
            interview.setTitle(dto.getTitle());
            interview.setContent(dto.getContent());
            interview.setImgUrl(dto.getImgUrl());
            intangibleInterviewMapper.insert(interview);
            return interview.getId();
        }
        if (Objects.equals(INFO_TYPE_KNOWLEDGE, dto.getInfoType())) {
            IntangibleKnowledge knowledge = new IntangibleKnowledge();
            knowledge.setPublisherId(userId);
            knowledge.setTitle(dto.getTitle());
            knowledge.setContent(dto.getContent());
            knowledge.setImgUrl(dto.getImgUrl());
            intangibleKnowledgeMapper.insert(knowledge);
            return knowledge.getId();
        }
        throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "仅支持新增访谈资料或知识窗口");
    }

    @Override
    public void updateInherit(Long id, InheritSaveDTO dto) {
        // 按入参信息类型定位所属拆分局表（前端编辑时锁定类型，infoType 与记录原类型一致）
        if (Objects.equals(INFO_TYPE_INTERVIEW, dto.getInfoType())) {
            IntangibleInterview interview = intangibleInterviewMapper.selectById(id);
            if (interview == null) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "传承信息不存在");
            }
            interview.setTitle(dto.getTitle());
            interview.setContent(dto.getContent());
            interview.setImgUrl(dto.getImgUrl());
            intangibleInterviewMapper.updateById(interview);
            return;
        }
        if (Objects.equals(INFO_TYPE_KNOWLEDGE, dto.getInfoType())) {
            IntangibleKnowledge knowledge = intangibleKnowledgeMapper.selectById(id);
            if (knowledge == null) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "传承信息不存在");
            }
            knowledge.setTitle(dto.getTitle());
            knowledge.setContent(dto.getContent());
            knowledge.setImgUrl(dto.getImgUrl());
            intangibleKnowledgeMapper.updateById(knowledge);
            return;
        }
        throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "传承信息不存在");
    }

    @Override
    public void removeInherit(Long id, Integer type) {
        // 三张拆分局表 ID 各自独立，需按类型定位；逻辑删除由 @TableLogic 置 is_deleted=1，前台查询自动过滤
        if (Objects.equals(INFO_TYPE_INTERVIEW, type)) {
            if (intangibleInterviewMapper.selectById(id) == null) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "传承信息不存在");
            }
            intangibleInterviewMapper.deleteById(id);
            return;
        }
        if (Objects.equals(INFO_TYPE_KNOWLEDGE, type)) {
            if (intangibleKnowledgeMapper.selectById(id) == null) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "传承信息不存在");
            }
            intangibleKnowledgeMapper.deleteById(id);
            return;
        }
        if (Objects.equals(INFO_TYPE_VIDEO, type)) {
            if (intangibleVideoMapper.selectById(id) == null) {
                throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "传承信息不存在");
            }
            intangibleVideoMapper.deleteById(id);
            return;
        }
        throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "传承信息不存在");
    }

    @Override
    public Long publishVideo(VideoPublishDTO dto, Long userId, Integer role) {
        if (role == null || role != ROLE_INHERITOR) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅非遗人可发布学习视频");
        }
        IntangibleVideo video = new IntangibleVideo();
        video.setPublisherId(userId);
        video.setTitle(dto.getTitle());
        video.setContent(dto.getContent());
        video.setVideoUrl(dto.getVideoUrl());
        // 封面缩略图：发布端截取视频画面生成（可选，缺失时前台回落视频首帧 / 默认封面）
        video.setImgUrl(dto.getImgUrl());
        video.setDuration(dto.getDuration());
        // 非遗人发布需管理员审核，进入待审核状态（audit_status=0，前台不可见）
        video.setAuditStatus(AUDIT_PENDING);
        intangibleVideoMapper.insert(video);
        return video.getId();
    }

    @Override
    public void auditVideo(Long id, Integer auditStatus, String remark, Long auditorId) {
        // 视频审核已基于独立视频表，无需再比对信息类型
        IntangibleVideo video = intangibleVideoMapper.selectById(id);
        if (video == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "学习视频不存在");
        }
        if (Objects.equals(AUDIT_REJECTED, auditStatus) && !StringUtils.hasText(remark)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "驳回时必须填写驳回原因");
        }
        video.setAuditStatus(auditStatus);
        // 驳回时记录不合格原因；通过时清空历史驳回原因
        video.setAuditRemark(Objects.equals(AUDIT_REJECTED, auditStatus) ? remark : null);
        video.setAuditTime(LocalDateTime.now());
        video.setAuditorId(auditorId);
        intangibleVideoMapper.updateById(video);
    }

    @Override
    public void updateMyVideo(Long id, VideoPublishDTO dto, Long userId) {
        IntangibleVideo video = intangibleVideoMapper.selectById(id);
        if (video == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "学习视频不存在或已删除");
        }
        if (!Objects.equals(video.getPublisherId(), userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅发布者本人可修改该视频");
        }
        // wrapper 显式 set 全字段（与实体 not-null 更新策略解耦，空封面 / 空时长可按编辑结果写空）；
        // 编辑后重新进入待审核，同时清空上一轮审核结果，避免“先审后改”绕过审核
        LambdaUpdateWrapper<IntangibleVideo> wrapper = new LambdaUpdateWrapper<IntangibleVideo>()
                .eq(IntangibleVideo::getId, id)
                .set(IntangibleVideo::getTitle, dto.getTitle())
                .set(IntangibleVideo::getContent, dto.getContent())
                .set(IntangibleVideo::getVideoUrl, dto.getVideoUrl())
                .set(IntangibleVideo::getImgUrl, dto.getImgUrl())
                .set(IntangibleVideo::getDuration, dto.getDuration())
                .set(IntangibleVideo::getAuditStatus, AUDIT_PENDING)
                .set(IntangibleVideo::getAuditRemark, null)
                .set(IntangibleVideo::getAuditTime, null)
                .set(IntangibleVideo::getAuditorId, null);
        intangibleVideoMapper.update(null, wrapper);
        log.info("编辑学习视频（重新进入待审核）：videoId={}, userId={}", id, userId);
    }

    @Override
    public void removeMyVideo(Long id, Long userId) {
        IntangibleVideo video = intangibleVideoMapper.selectById(id);
        if (video == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "学习视频不存在或已删除");
        }
        if (!Objects.equals(video.getPublisherId(), userId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "仅发布者本人可删除该视频");
        }
        // 逻辑删除（@TableLogic 置 is_deleted=1）：前台公开列表 / 我发布的 / 我学习的自动过滤；学习记录保留但随视频失效隐藏
        intangibleVideoMapper.deleteById(id);
        log.info("删除学习视频（逻辑删除）：videoId={}, userId={}", id, userId);
    }

    @Override
    public PageResult<IntangibleVideo> pageMyPublish(PageDto pageDto, Long userId) {
        // 本人发布的全部学习视频（含待审核 / 已驳回，便于作者跟踪进度），逻辑删除由 @TableLogic 自动过滤
        Page<IntangibleVideo> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        LambdaQueryWrapper<IntangibleVideo> wrapper = new LambdaQueryWrapper<IntangibleVideo>()
                .eq(IntangibleVideo::getPublisherId, userId)
                .orderByDesc(IntangibleVideo::getCreateTime)
                .orderByDesc(IntangibleVideo::getId);
        return PageResult.of(intangibleVideoMapper.selectPage(page, wrapper));
    }

    @Override
    public void reportWatch(Long videoId, Long userId) {
        // 仅记录仍有效且已审核通过的学习视频（未过审 / 已下架视频不产生学习记录）
        IntangibleVideo video = intangibleVideoMapper.selectById(videoId);
        if (video == null || !Objects.equals(video.getAuditStatus(), AUDIT_PASSED)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "学习视频不存在或未发布");
        }
        // 幂等 upsert：重复完整观看仅刷新最近观看时间（唯一键 uk_user_video 去重）
        userWatchRecordMapper.upsertWatch(userId, videoId);
    }

    @Override
    public PageResult<MyWatchVO> pageMyWatch(PageDto pageDto, Long userId) {
        // 自定义 SQL：INNER JOIN 仅保留仍有效且已过审的视频，is_deleted 已在 SQL 中手动过滤
        Page<MyWatchVO> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        IPage<MyWatchVO> result = userWatchRecordMapper.selectMyWatchPage(page, userId);
        return PageResult.of(result);
    }

    @Override
    public List<InheritSearchVO> searchMaterials(String keyword, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int size = limit == null || limit <= 0 ? SEARCH_DEFAULT_LIMIT : Math.min(limit, SEARCH_MAX_LIMIT);
        // 空格分隔多个关键词（如“苗绣 绉绣”）：命中任一关键词即匹配
        List<String> words = Arrays.stream(keyword.trim().split("\\s+"))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (words.isEmpty()) {
            return List.of();
        }
        List<InheritSearchVO> result = new ArrayList<>();
        // 访谈资料（管理员直发，无审核环节）
        LambdaQueryWrapper<IntangibleInterview> interviewWrapper = keywordWrapper(
                IntangibleInterview::getTitle, IntangibleInterview::getContent, words);
        interviewWrapper.orderByDesc(IntangibleInterview::getCreateTime)
                .orderByDesc(IntangibleInterview::getId)
                .last("LIMIT " + size);
        for (IntangibleInterview item : intangibleInterviewMapper.selectList(interviewWrapper)) {
            result.add(toSearchVO(INFO_TYPE_INTERVIEW, item.getId(), item.getTitle(), item.getContent()));
        }
        // 知识窗口（管理员直发，无审核环节）
        LambdaQueryWrapper<IntangibleKnowledge> knowledgeWrapper = keywordWrapper(
                IntangibleKnowledge::getTitle, IntangibleKnowledge::getContent, words);
        knowledgeWrapper.orderByDesc(IntangibleKnowledge::getCreateTime)
                .orderByDesc(IntangibleKnowledge::getId)
                .last("LIMIT " + size);
        for (IntangibleKnowledge item : intangibleKnowledgeMapper.selectList(knowledgeWrapper)) {
            result.add(toSearchVO(INFO_TYPE_KNOWLEDGE, item.getId(), item.getTitle(), item.getContent()));
        }
        // 视频学习：仅已审核通过（前台可见）的视频，content 为视频简介
        LambdaQueryWrapper<IntangibleVideo> videoWrapper = keywordWrapper(
                IntangibleVideo::getTitle, IntangibleVideo::getContent, words);
        videoWrapper.eq(IntangibleVideo::getAuditStatus, AUDIT_PASSED)
                .orderByDesc(IntangibleVideo::getCreateTime)
                .orderByDesc(IntangibleVideo::getId)
                .last("LIMIT " + size);
        for (IntangibleVideo item : intangibleVideoMapper.selectList(videoWrapper)) {
            result.add(toSearchVO(INFO_TYPE_VIDEO, item.getId(), item.getTitle(), item.getContent()));
        }
        return result;
    }

    // ———————————————— 管理端首页数据统计 ————————————————

    @Override
    public AdminStatsVO adminStats() {
        AdminStatsVO vo = new AdminStatsVO();
        // 1.论坛：帖子总量 / 待审核数 / 回复总量（逻辑删除记录自动排除）
        vo.setPostTotal(forumPostMapper.selectCount(null));
        vo.setPostPending(forumPostMapper.selectCount(new QueryWrapper<ForumPost>()
                .eq("audit_status", AUDIT_PENDING)));
        vo.setReplyTotal(forumReplyMapper.selectCount(null));
        // 2.学习视频：总量 / 待审核数（0待审核 1已通过 2已驳回）
        vo.setVideoTotal(intangibleVideoMapper.selectCount(null));
        vo.setVideoPending(intangibleVideoMapper.selectCount(new QueryWrapper<IntangibleVideo>()
                .eq("audit_status", AUDIT_PENDING)));
        return vo;
    }

    /** 检索条件：标题或正文命中任一关键词（多词 OR 平铺），供三类资料复用；（逻辑删除由 @TableLogic 自动过滤） */
    private <T> LambdaQueryWrapper<T> keywordWrapper(SFunction<T, ?> titleColumn,
                                                     SFunction<T, ?> contentColumn,
                                                     List<String> words) {
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) {
                    w.or();
                }
                w.like(titleColumn, words.get(i)).or().like(contentColumn, words.get(i));
            }
        });
        return wrapper;
    }

    /** 组装检索结果：正文转为纯文本片段（Agent 引用作答用） */
    private InheritSearchVO toSearchVO(Integer infoType, Long id, String title, String content) {
        InheritSearchVO vo = new InheritSearchVO();
        vo.setInfoType(infoType);
        vo.setId(id);
        vo.setTitle(title);
        vo.setSnippet(buildSnippet(content));
        return vo;
    }

    /** 富文本正文 → 纯文本片段：剥离 HTML 标签、压缩连续空白、超长截断 */
    private String buildSnippet(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String plain = WHITESPACE_PATTERN
                .matcher(HTML_TAG_PATTERN.matcher(content).replaceAll(" "))
                .replaceAll(" ")
                .trim();
        return plain.length() > SEARCH_SNIPPET_MAX ? plain.substring(0, SEARCH_SNIPPET_MAX) + "…" : plain;
    }
}
