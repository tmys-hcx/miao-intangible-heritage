package com.mic.inheritReserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.inheritReserve.model.entity.ForumReply;
import com.mic.inheritReserve.model.vo.ForumReplyVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 论坛回复表Mapper
 * 说明：自定义SQL不经过 @TableLogic 拦截，is_deleted 条件显式声明；
 * 联表 sys_user（同库）回填回复者昵称 / 头像 / 角色，二级回复额外回填被回复人昵称；
 * 子查询统计评论点赞数（inherit_forum_reply_like 有效行）
 */
public interface ForumReplyMapper extends BaseMapper<ForumReply> {

    /** 顶级回复分页（parent_id=0），联表取回复者昵称头像，子查询统计点赞数，按时间倒序（最新在前） */
    @Select("""
            SELECT r.id, r.post_id, r.user_id, r.parent_id, r.reply_user_id,
                   r.reply_content, r.create_time,
                   u.nickname, u.avatar, u.role AS user_role,
                   (SELECT COUNT(*) FROM inherit_forum_reply_like rl
                    WHERE rl.reply_id = r.id AND rl.is_deleted = 0) AS like_count
            FROM inherit_forum_reply r
            LEFT JOIN sys_user u ON u.id = r.user_id AND u.is_deleted = 0
            WHERE r.is_deleted = 0 AND r.post_id = #{postId} AND r.parent_id = 0
            ORDER BY r.create_time DESC, r.id DESC
            """)
    IPage<ForumReplyVO> selectTopPage(IPage<ForumReplyVO> page, @Param("postId") Long postId);

    /** 查询顶级回复下的全部二级回复：联表取回复者与被回复人昵称，子查询统计点赞数，按时间正序（对话顺序） */
    @Select("""
            <script>
            SELECT r.id, r.post_id, r.user_id, r.parent_id, r.reply_user_id,
                   r.reply_content, r.create_time,
                   u.nickname, u.avatar, u.role AS user_role,
                   ru.nickname AS reply_nickname,
                   (SELECT COUNT(*) FROM inherit_forum_reply_like rl
                    WHERE rl.reply_id = r.id AND rl.is_deleted = 0) AS like_count
            FROM inherit_forum_reply r
            LEFT JOIN sys_user u ON u.id = r.user_id AND u.is_deleted = 0
            LEFT JOIN sys_user ru ON ru.id = r.reply_user_id AND ru.is_deleted = 0
            WHERE r.is_deleted = 0 AND r.parent_id IN
            <foreach collection="parentIds" item="parentId" open="(" separator="," close=")">
                #{parentId}
            </foreach>
            ORDER BY r.create_time ASC, r.id ASC
            </script>
            """)
    List<ForumReplyVO> selectRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    /** 按ID查询单条回复VO（发布成功后返回，联表回填昵称头像与被回复人昵称，子查询统计点赞数） */
    @Select("""
            SELECT r.id, r.post_id, r.user_id, r.parent_id, r.reply_user_id,
                   r.reply_content, r.create_time,
                   u.nickname, u.avatar, u.role AS user_role,
                   ru.nickname AS reply_nickname,
                   (SELECT COUNT(*) FROM inherit_forum_reply_like rl
                    WHERE rl.reply_id = r.id AND rl.is_deleted = 0) AS like_count
            FROM inherit_forum_reply r
            LEFT JOIN sys_user u ON u.id = r.user_id AND u.is_deleted = 0
            LEFT JOIN sys_user ru ON ru.id = r.reply_user_id AND ru.is_deleted = 0
            WHERE r.id = #{id} AND r.is_deleted = 0
            """)
    ForumReplyVO selectVOById(@Param("id") Long id);

    /** 连带逻辑删除某顶级回复下的全部二级回复（绕过 @TableLogic 自动过滤） */
    @Update("UPDATE inherit_forum_reply SET is_deleted = 1 WHERE parent_id = #{parentId} AND is_deleted = 0")
    int updateDeletedByParentId(@Param("parentId") Long parentId);
}
