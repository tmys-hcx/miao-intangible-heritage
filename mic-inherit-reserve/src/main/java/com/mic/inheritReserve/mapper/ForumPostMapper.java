package com.mic.inheritReserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.inheritReserve.model.entity.ForumPost;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 论坛帖子Mapper
 */
public interface ForumPostMapper extends BaseMapper<ForumPost> {

    /**
     * 公开分页：LEFT JOIN sys_user / inherit_forum_topic 填充作者昵称 / 头像与话题名，
     * 子查询统计回复数 / 点赞数（inherit_forum_reply / inherit_forum_like 有效行），按发布时间倒序；
     * 仅展示已通过（audit_status=1）帖子，可按标题 / 正文关键词模糊搜索（论坛搜索框）；
     * 说明：自定义 SQL 不经过 @TableLogic 拦截，需手动过滤 is_deleted = 0
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param keyword 搜索关键词（标题或正文模糊匹配），null/空=不过滤
     */
    @Select("""
            <script>
            SELECT p.*, u.nickname AS publisher_nickname, u.avatar AS publisher_avatar, t.name AS topic_name,
                   (SELECT COUNT(*) FROM inherit_forum_reply r
                    WHERE r.post_id = p.id AND r.is_deleted = 0) AS reply_count,
                   (SELECT COUNT(*) FROM inherit_forum_like fl
                    WHERE fl.post_id = p.id AND fl.is_deleted = 0) AS like_count
            FROM inherit_forum_post p
            LEFT JOIN sys_user u ON u.id = p.publisher_id AND u.is_deleted = 0
            LEFT JOIN inherit_forum_topic t ON t.id = p.topic_id AND t.is_deleted = 0
            WHERE p.is_deleted = 0 AND p.audit_status = 1
            <if test="keyword != null and keyword != ''">
                AND (p.title LIKE CONCAT('%', #{keyword}, '%') OR p.content LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY p.create_time DESC, p.id DESC
            </script>
            """)
    IPage<ForumPost> selectPublicPage(IPage<ForumPost> page, @Param("keyword") String keyword);

    /**
     * 帖子详情：LEFT JOIN sys_user / inherit_forum_topic 填充作者昵称 / 头像与话题名，
     * 子查询统计回复数 / 点赞数（inherit_forum_reply / inherit_forum_like 有效行）；仅要求未删除，
     * 审核状态过滤由服务层完成（未通过帖子仅作者本人可预览，访客一律视为不存在）
     * @param id 帖子ID
     * @return 帖子详情（不存在 / 已删除返回 null；命中未通过帖子时由服务层判权）
     */
    @Select("""
            SELECT p.*, u.nickname AS publisher_nickname, u.avatar AS publisher_avatar, t.name AS topic_name,
                   (SELECT COUNT(*) FROM inherit_forum_reply r
                    WHERE r.post_id = p.id AND r.is_deleted = 0) AS reply_count,
                   (SELECT COUNT(*) FROM inherit_forum_like fl
                    WHERE fl.post_id = p.id AND fl.is_deleted = 0) AS like_count
            FROM inherit_forum_post p
            LEFT JOIN sys_user u ON u.id = p.publisher_id AND u.is_deleted = 0
            LEFT JOIN inherit_forum_topic t ON t.id = p.topic_id AND t.is_deleted = 0
            WHERE p.id = #{id} AND p.is_deleted = 0
            """)
    ForumPost selectDetailById(@Param("id") Long id);

    /**
     * 我的帖子分页（个人中心「我发布的帖子」）：本人全部审核状态（含待审核 / 已驳回），
     * LEFT JOIN inherit_forum_topic 填充话题名，子查询统计回复数 / 点赞数，按发布时间倒序；手动过滤 is_deleted = 0
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param publisherId 当前登录用户ID
     */
    @Select("""
            SELECT p.*, t.name AS topic_name,
                   (SELECT COUNT(*) FROM inherit_forum_reply r
                    WHERE r.post_id = p.id AND r.is_deleted = 0) AS reply_count,
                   (SELECT COUNT(*) FROM inherit_forum_like fl
                    WHERE fl.post_id = p.id AND fl.is_deleted = 0) AS like_count
            FROM inherit_forum_post p
            LEFT JOIN inherit_forum_topic t ON t.id = p.topic_id AND t.is_deleted = 0
            WHERE p.is_deleted = 0 AND p.publisher_id = #{publisherId}
            ORDER BY p.create_time DESC, p.id DESC
            """)
    IPage<ForumPost> selectMyPage(IPage<ForumPost> page, @Param("publisherId") Long publisherId);

    /**
     * 管理端分页（论坛审核）：LEFT JOIN sys_user / inherit_forum_topic 填充作者昵称 / 头像与话题名，
     * 子查询统计回复数 / 点赞数，按发布时间倒序；可按审核状态 / 标题关键词筛选（默认全部状态）
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param auditStatus 审核状态（0待审核 1已通过 2已驳回），null=全部
     * @param keyword 标题关键词（模糊匹配），null/空=不过滤
     */
    @Select("""
            <script>
            SELECT p.*, u.nickname AS publisher_nickname, u.avatar AS publisher_avatar, t.name AS topic_name,
                   (SELECT COUNT(*) FROM inherit_forum_reply r
                    WHERE r.post_id = p.id AND r.is_deleted = 0) AS reply_count,
                   (SELECT COUNT(*) FROM inherit_forum_like fl
                    WHERE fl.post_id = p.id AND fl.is_deleted = 0) AS like_count
            FROM inherit_forum_post p
            LEFT JOIN sys_user u ON u.id = p.publisher_id AND u.is_deleted = 0
            LEFT JOIN inherit_forum_topic t ON t.id = p.topic_id AND t.is_deleted = 0
            WHERE p.is_deleted = 0
            <if test="auditStatus != null">
                AND p.audit_status = #{auditStatus}
            </if>
            <if test="keyword != null and keyword != ''">
                AND p.title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY p.create_time DESC, p.id DESC
            </script>
            """)
    IPage<ForumPost> selectAdminPage(IPage<ForumPost> page,
                                     @Param("auditStatus") Integer auditStatus,
                                     @Param("keyword") String keyword);

    /**
     * 按ID查询帖子简要信息（仅少量列：id / publisher_id / topic_id / audit_status）：作者标注、删除权限、
     * 话题计数、点赞与评论校验等只需少量字段的场景，避免 MP 全列查询对表结构的强依赖
     * @param id 帖子ID
     * @return 帖子简要信息（不存在 / 已删除返回 null）
     */
    @Select("SELECT id, publisher_id, topic_id, audit_status FROM inherit_forum_post WHERE id = #{id} AND is_deleted = 0")
    ForumPost selectBriefById(@Param("id") Long id);
}
