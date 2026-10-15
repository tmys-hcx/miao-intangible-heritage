package com.mic.inheritReserve.service;

import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.inheritReserve.model.dto.ForumPublishDTO;
import com.mic.inheritReserve.model.dto.ForumReplyDTO;
import com.mic.inheritReserve.model.entity.ForumPost;
import com.mic.inheritReserve.model.entity.ForumTopic;
import com.mic.inheritReserve.model.vo.ForumReplyVO;

import java.util.List;

/**
 * 论坛服务接口（传承与学习-论坛板块）：帖子发布 / 编辑 / 删除 + 公开分页 / 详情 / 话题
 * + 两级回复（分页 / 发表 / 删除，结构对齐 culture_comment）
 * 审核制：发帖 / 编辑后进入待审核（0），管理员审核通过（1）后前台展示；
 * 个人中心「我发布的帖子」展示本人全部状态，管理端按状态筛选审核
 */
public interface ForumService {

    /**
     * 公开分页：仅已通过（audit_status=1）帖子，按发布时间倒序；
     * 作者昵称 / 话题名 / 回复数由 SQL 联表与统计填充；可按标题 / 正文关键词搜索（论坛搜索框）
     *
     * @param pageDto 分页参数
     * @param keyword 搜索关键词（标题 / 正文模糊匹配），null/空=不过滤
     * @return 分页结果（records 为 ForumPost，瞬态字段已回填）
     */
    PageResult<ForumPost> pagePublic(PageDto pageDto, String keyword);

    /**
     * 话题列表：按使用次数倒序（发布卡片「#」浮层选择用）
     */
    List<ForumTopic> listTopics();

    /**
     * 发布帖子（登录用户）：提交后进入待审核（audit_status=0），管理员审核通过后前台展示；
     * 话题可选，选用时话题使用次数 +1
     *
     * @param dto    发布入参
     * @param userId 发帖人ID（登录用户）
     * @return 新帖子ID
     */
    Long publishPost(ForumPublishDTO dto, Long userId);

    /**
     * 编辑帖子（仅作者本人）：编辑后重新进入待审核（重置上一轮审核结果），管理员重审通过后展示；
     * 话题变更时同步维护新旧话题使用次数（旧 -1、新 +1）
     *
     * @param id     帖子ID
     * @param dto    编辑入参
     * @param userId 当前登录用户ID（非作者本人拒绝）
     */
    void updatePost(Long id, ForumPublishDTO dto, Long userId);

    /**
     * 删除帖子（仅作者本人，逻辑删除）：删除后对应话题使用次数 -1
     *
     * @param id     帖子ID
     * @param userId 当前登录用户ID（非作者本人拒绝）
     */
    void removePost(Long id, Long userId);

    /**
     * 帖子详情：未删除即可命中；已通过（audit_status=1）对所有人可见，
     * 未通过（待审核 / 已驳回）仅作者本人可预览（个人中心入口），访客一律视为不存在
     *
     * @param id     帖子ID
     * @param userId 当前登录用户ID（可为空，未登录视为访客）
     * @return 帖子详情（不存在 / 无权查看抛 400）
     */
    ForumPost getDetail(Long id, Long userId);

    /**
     * 我发布的帖子分页（登录用户）：本人全部审核状态（含待审核 / 已驳回与驳回原因），
     * 含话题名 / 回复数，按发布时间倒序
     *
     * @param pageDto 分页参数
     * @param userId  当前登录用户ID
     * @return 分页结果（records 为 ForumPost）
     */
    PageResult<ForumPost> pageMyPublish(PageDto pageDto, Long userId);

    /**
     * 后台帖子分页（管理员）：全部帖子（含未通过），可按审核状态 / 标题关键词筛选
     *
     * @param pageDto     分页参数
     * @param auditStatus 审核状态（0待审核 1已通过 2已驳回），null=全部
     * @param keyword     标题关键词（模糊匹配），null/空=不过滤
     * @return 分页结果（records 为 ForumPost，含作者昵称 / 话题名 / 回复数）
     */
    PageResult<ForumPost> pageAdmin(PageDto pageDto, Integer auditStatus, String keyword);

    /**
     * 帖子审核（管理员）：通过（1）前台即刻展示；驳回（2）需填写原因供作者查看并修改重新提交
     *
     * @param id          帖子ID
     * @param auditStatus 审核结论（1通过 / 2驳回）
     * @param remark      审核备注（驳回时必填）
     * @param auditorId   审核管理员ID
     */
    void auditPost(Long id, Integer auditStatus, String remark, Long auditorId);

    /**
     * 回复分页（两级结构）：顶级回复分页 + 二级回复归拢到各自顶级回复；作者回复标注 isAuthor
     *
     * @param postId  帖子ID
     * @param pageDto 分页参数
     * @return 分页结果（records 为顶级回复，含 replies 二级列表）
     */
    PageResult<ForumReplyVO> pageReplies(Long postId, PageDto pageDto);

    /**
     * 发表回复（登录用户）：parentId 为空 / 0 为顶级回复；
     * 回复他人时后端归一化（parent_id 指向所属顶级回复，reply_user_id 记录被回复人）
     *
     * @param dto    回复入参
     * @param userId 回复人ID（登录用户）
     * @return 新回复VO（前端按 parentId 插入列表对应位置）
     */
    ForumReplyVO publishReply(ForumReplyDTO dto, Long userId);

    /**
     * 删除回复（回复作者本人 或 帖子作者；顶级回复连带删除其二级回复）
     *
     * @param id     回复ID
     * @param userId 当前登录用户ID（非回复作者 / 帖子作者拒绝）
     */
    void removeReply(Long id, Long userId);
}
