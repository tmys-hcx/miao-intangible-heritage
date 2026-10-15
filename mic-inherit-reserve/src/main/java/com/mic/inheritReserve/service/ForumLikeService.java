package com.mic.inheritReserve.service;

import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.inheritReserve.model.entity.ForumPost;

import java.util.List;

/**
 * 论坛点赞服务接口（帖子点赞 + 评论点赞）：
 * 点赞 / 取消均幂等（取消后再次点赞恢复原记录），同一用户对同一目标仅保留一条有效记录；
 * 个人中心「我点赞的帖子」仅展示仍有效的已通过帖子；评论点赞状态供详情页批量回显
 */
public interface ForumLikeService {

    /**
     * 点赞帖子（登录用户）：仅已通过帖子可点赞；重复点赞幂等返回
     *
     * @param postId 帖子ID
     * @param userId 点赞用户ID
     */
    void likePost(Long postId, Long userId);

    /**
     * 取消点赞帖子（登录用户）：未点赞 / 已取消时幂等返回
     *
     * @param postId 帖子ID
     * @param userId 当前登录用户ID
     */
    void cancelPostLike(Long postId, Long userId);

    /**
     * 帖子点赞状态（详情页回显）：未登录 / 未点赞均返回 false
     *
     * @param postId 帖子ID
     * @param userId 当前登录用户ID（可为空，游客视为未点赞）
     * @return 是否已点赞
     */
    Boolean getPostLikeState(Long postId, Long userId);

    /**
     * 我点赞的帖子分页（登录用户）：仅仍有效的已通过帖子（被删除 / 未通过审核自动排除），
     * 含作者昵称 / 话题名 / 评论数 / 点赞数，按点赞时间倒序
     *
     * @param pageDto 分页参数
     * @param userId  当前登录用户ID
     * @return 分页结果（records 为 ForumPost）
     */
    PageResult<ForumPost> pageMyLiked(PageDto pageDto, Long userId);

    /**
     * 点赞评论（登录用户）：仅存在且所属帖子已通过的评论可点赞；重复点赞幂等返回
     *
     * @param replyId 评论（回复）ID
     * @param userId  点赞用户ID
     */
    void likeReply(Long replyId, Long userId);

    /**
     * 取消点赞评论（登录用户）：未点赞 / 已取消时幂等返回
     *
     * @param replyId 评论（回复）ID
     * @param userId  当前登录用户ID
     */
    void cancelReplyLike(Long replyId, Long userId);

    /**
     * 批量查询当前用户已点赞的评论ID（评论列表点赞状态回显）
     *
     * @param userId   当前登录用户ID（可为空，游客返回空列表）
     * @param replyIds 评论ID集合（空集合直接返回空列表）
     * @return 已点赞的评论ID列表
     */
    List<Long> listLikedReplyIds(Long userId, List<Long> replyIds);
}
