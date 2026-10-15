package com.mic.inheritReserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.inheritReserve.model.entity.ForumLike;
import com.mic.inheritReserve.model.entity.ForumPost;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 论坛帖子点赞表Mapper
 * 说明：有效状态通过 is_deleted 切换（0有效 / 1取消），同一（用户+帖子）仅保留一条物理记录，
 * 避免唯一键 uk_user_post 在反复点赞 / 取消时冲突；自定义SQL不受 @TableLogic 自动过滤，条件显式声明
 */
public interface ForumLikeMapper extends BaseMapper<ForumLike> {

    /**
     * 查询点赞记录（含已取消记录）
     * 取消后再次点赞时恢复原记录，规避唯一键冲突
     */
    @Select("SELECT * FROM inherit_forum_like WHERE user_id = #{userId} AND post_id = #{postId} LIMIT 1")
    ForumLike selectIncludingDeleted(@Param("userId") Long userId, @Param("postId") Long postId);

    /** 切换逻辑删除标记（0恢复 / 1取消）：自定义SQL绕过 @TableLogic 自动过滤 */
    @Update("UPDATE inherit_forum_like SET is_deleted = #{isDeleted} WHERE id = #{id}")
    int updateDeletedById(@Param("id") Long id, @Param("isDeleted") Integer isDeleted);

    /** 查询当前用户对指定帖子的有效点赞记录（详情页点赞状态回显，未点赞返回 null） */
    @Select("SELECT * FROM inherit_forum_like WHERE user_id = #{userId} AND post_id = #{postId} AND is_deleted = 0")
    ForumLike selectActiveByUserAndPost(@Param("userId") Long userId, @Param("postId") Long postId);

    /**
     * 我点赞的帖子分页（个人中心「我点赞的帖子」）：连表查询仍有效的已通过帖子
     * （帖子被删除 / 未通过审核时自动排除），含作者昵称 / 头像 / 话题名 / 评论数 / 点赞数，按点赞时间倒序
     */
    @Select("""
            SELECT p.*, u.nickname AS publisher_nickname, u.avatar AS publisher_avatar, t.name AS topic_name,
                   (SELECT COUNT(*) FROM inherit_forum_reply r
                    WHERE r.post_id = p.id AND r.is_deleted = 0) AS reply_count,
                   (SELECT COUNT(*) FROM inherit_forum_like fl2
                    WHERE fl2.post_id = p.id AND fl2.is_deleted = 0) AS like_count
            FROM inherit_forum_like fl
            INNER JOIN inherit_forum_post p
                ON p.id = fl.post_id AND p.is_deleted = 0 AND p.audit_status = 1
            LEFT JOIN sys_user u ON u.id = p.publisher_id AND u.is_deleted = 0
            LEFT JOIN inherit_forum_topic t ON t.id = p.topic_id AND t.is_deleted = 0
            WHERE fl.user_id = #{userId} AND fl.is_deleted = 0
            ORDER BY fl.create_time DESC, fl.id DESC
            """)
    IPage<ForumPost> selectMyLikedPage(IPage<ForumPost> page, @Param("userId") Long userId);
}
