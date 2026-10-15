package com.mic.inheritReserve.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.inheritReserve.model.dto.ForumAuditDTO;
import com.mic.inheritReserve.model.dto.ForumPublishDTO;
import com.mic.inheritReserve.model.dto.ForumReplyDTO;
import com.mic.inheritReserve.model.entity.ForumPost;
import com.mic.inheritReserve.model.entity.ForumTopic;
import com.mic.inheritReserve.model.vo.ForumReplyVO;
import com.mic.inheritReserve.service.ForumLikeService;
import com.mic.inheritReserve.service.ForumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 论坛接口（传承与学习-论坛板块）：
 * - 公开：帖子分页（支持标题 / 正文关键词搜索）/ 详情（仅已通过 audit_status=1 对所有人展示；未通过帖子仅作者本人可预览）
 *         + 话题列表（按使用热度倒序）+ 评论分页（顶级评论 + 二级评论两级组装）
 * - 登录用户：发布帖子（提交后进入待审核）+ 编辑（编辑后重新进入待审核）/ 删除（仅作者本人）
 *             + 发表评论（评论他人自动归一化）+ 删除评论（评论作者本人或帖子作者）
 *             + 帖子点赞 / 评论点赞（可取消，重复点赞幂等；点赞数与作者昵称等随列表 / 详情返回）
 *             + 个人中心「我发布的帖子」（本人全部审核状态，含驳回原因）+「我点赞的帖子」（仅仍有效的已通过帖子）
 * - 管理员：后台帖子分页（可按审核状态 / 标题关键词筛选）+ 帖子审核（通过 / 驳回）
 * 登录态说明：网关鉴权通过后透传 X-User-Id / X-User-Role，服务据此识别当前用户与角色
 */
@Tag(name = "论坛接口")
@RestController
@RequestMapping("/api/inherit/forum")
@RequiredArgsConstructor
public class ForumController {

    /** 角色：0管理员（论坛帖子审核） */
    private static final int ROLE_ADMIN = 0;

    private final ForumService forumService;
    private final ForumLikeService forumLikeService;

    @Operation(summary = "公开帖子分页（仅已通过审核）：含作者昵称 / 话题名 / 评论数 / 点赞数，按发布时间倒序，可按标题 / 正文关键词搜索")
    @GetMapping("/page")
    public Result<PageResult<ForumPost>> page(PageDto pageDto,
                                              @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(forumService.pagePublic(pageDto, keyword));
    }

    @Operation(summary = "帖子详情：已通过帖子公开；未通过帖子仅作者本人可预览（其他访客视为不存在）")
    @GetMapping("/{id}")
    public Result<ForumPost> detail(@PathVariable("id") Long id,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.ok(forumService.getDetail(id, userId));
    }

    @Operation(summary = "话题列表（公开）：按使用次数倒序，供发布卡片「#」浮层选择")
    @GetMapping("/topic/list")
    public Result<List<ForumTopic>> topicList() {
        return Result.ok(forumService.listTopics());
    }

    @Operation(summary = "发布帖子（登录用户）：提交后进入待审核，管理员审核通过后公开展示；话题可选，选用后话题使用次数+1")
    @PostMapping("/publish")
    public Result<Long> publish(@Valid @RequestBody ForumPublishDTO dto,
                                @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(forumService.publishPost(dto, userId));
    }

    @Operation(summary = "编辑帖子（仅作者本人）：编辑后重新进入待审核；话题变更时同步维护新旧话题使用次数（旧-1、新+1）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody ForumPublishDTO dto,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        forumService.updatePost(id, dto, userId);
        return Result.ok();
    }

    @Operation(summary = "删除帖子（仅作者本人，逻辑删除）：删除后对应话题使用次数-1")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        forumService.removePost(id, userId);
        return Result.ok();
    }

    @Operation(summary = "我发布的帖子分页（登录用户）：本人全部审核状态（含待审核 / 已驳回与驳回原因），按发布时间倒序")
    @GetMapping("/my/page")
    public Result<PageResult<ForumPost>> myPage(PageDto pageDto,
                                                @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(forumService.pageMyPublish(pageDto, userId));
    }

    @Operation(summary = "后台帖子分页（管理员）：全部帖子（含未通过），可按审核状态 / 标题关键词筛选")
    @GetMapping("/admin/page")
    public Result<PageResult<ForumPost>> adminPage(
            PageDto pageDto,
            @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        return Result.ok(forumService.pageAdmin(pageDto, auditStatus, keyword));
    }

    @Operation(summary = "帖子审核（管理员）：auditStatus=1审核通过、2审核拒绝（驳回，remark必填）")
    @PutMapping("/audit/{id}")
    public Result<Void> audit(@PathVariable("id") Long id,
                              @Valid @RequestBody ForumAuditDTO dto,
                              @RequestHeader(value = "X-User-Id", required = false) Long userId,
                              @RequestHeader(value = "X-User-Role", required = false) Integer role) {
        checkAdmin(role);
        checkLogin(userId);
        forumService.auditPost(id, dto.getAuditStatus(), dto.getRemark(), userId);
        return Result.ok();
    }

    @Operation(summary = "评论分页（公开）：仅已通过帖子可查；顶级评论分页 + 二级评论组装，含点赞数")
    @GetMapping("/reply/page")
    public Result<PageResult<ForumReplyVO>> replyPage(PageDto pageDto,
                                                      @RequestParam(value = "postId") Long postId) {
        return Result.ok(forumService.pageReplies(postId, pageDto));
    }

    @Operation(summary = "发表评论（登录用户）：parentId 为空为顶级评论，评论他人时后端归一化")
    @PostMapping("/reply")
    public Result<ForumReplyVO> publishReply(@Valid @RequestBody ForumReplyDTO dto,
                                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(forumService.publishReply(dto, userId));
    }

    @Operation(summary = "删除评论（评论作者本人 或 帖子作者，顶级评论连带删除其评论）")
    @DeleteMapping("/reply/{id}")
    public Result<Void> removeReply(@PathVariable("id") Long id,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        forumService.removeReply(id, userId);
        return Result.ok();
    }

    @Operation(summary = "点赞帖子（登录用户）：仅已通过帖子可点赞，重复点赞幂等")
    @PostMapping("/like/{postId}")
    public Result<Void> likePost(@PathVariable("postId") Long postId,
                                 @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        forumLikeService.likePost(postId, userId);
        return Result.ok();
    }

    @Operation(summary = "取消点赞帖子（登录用户）：未点赞 / 已取消时幂等")
    @DeleteMapping("/like/{postId}")
    public Result<Void> cancelPostLike(@PathVariable("postId") Long postId,
                                       @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        forumLikeService.cancelPostLike(postId, userId);
        return Result.ok();
    }

    @Operation(summary = "帖子点赞状态（登录用户）：未登录 / 未点赞返回 false，供详情页回显")
    @GetMapping("/like/state")
    public Result<Boolean> postLikeState(@RequestParam(value = "postId") Long postId,
                                         @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.ok(forumLikeService.getPostLikeState(postId, userId));
    }

    @Operation(summary = "我点赞的帖子分页（登录用户）：仅仍有效的已通过帖子，按点赞时间倒序（个人中心）")
    @GetMapping("/like/my/page")
    public Result<PageResult<ForumPost>> myLikedPage(PageDto pageDto,
                                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(forumLikeService.pageMyLiked(pageDto, userId));
    }

    @Operation(summary = "点赞评论（登录用户）：仅已通过帖子下的评论可点赞，重复点赞幂等")
    @PostMapping("/reply/like/{replyId}")
    public Result<Void> likeReply(@PathVariable("replyId") Long replyId,
                                  @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        forumLikeService.likeReply(replyId, userId);
        return Result.ok();
    }

    @Operation(summary = "取消点赞评论（登录用户）：未点赞 / 已取消时幂等")
    @DeleteMapping("/reply/like/{replyId}")
    public Result<Void> cancelReplyLike(@PathVariable("replyId") Long replyId,
                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        forumLikeService.cancelReplyLike(replyId, userId);
        return Result.ok();
    }

    @Operation(summary = "评论点赞状态批量回显（登录用户）：传入评论ID集合，返回当前用户已点赞的评论ID列表")
    @GetMapping("/reply/like/state")
    public Result<List<Long>> replyLikeStates(@RequestParam(value = "replyIds") List<Long> replyIds,
                                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.ok(forumLikeService.listLikedReplyIds(userId, replyIds));
    }

    /** 网关鉴权通过后透传 X-User-Id，缺失说明未登录 */
    private void checkLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
    }

    /** 校验管理员角色：网关鉴权通过后透传 X-User-Role 可信头，非0（管理员）拒绝操作 */
    private void checkAdmin(Integer role) {
        if (role == null || role != ROLE_ADMIN) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN, "无操作权限，仅管理员可审核论坛帖子");
        }
    }
}
