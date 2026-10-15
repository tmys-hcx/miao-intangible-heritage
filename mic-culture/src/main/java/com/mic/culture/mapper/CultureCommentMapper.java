package com.mic.culture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.culture.model.entity.CultureComment;
import com.mic.culture.model.vo.CultureCommentVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 评论表Mapper
 * 说明：自定义SQL不经过 @TableLogic 拦截，is_deleted 条件显式声明；
 * 联表 sys_user（同库）回填评论者昵称 / 头像 / 角色，二级回复额外回填被回复人昵称
 */
public interface CultureCommentMapper extends BaseMapper<CultureComment> {

    /** 顶级评论分页（parent_id=0），联表取评论者昵称头像，按时间倒序（最新在前） */
    @Select("""
            SELECT cc.id, cc.culture_id, cc.user_id, cc.parent_id, cc.reply_user_id,
                   cc.comment_content, cc.create_time,
                   u.nickname, u.avatar, u.role AS user_role
            FROM culture_comment cc
            LEFT JOIN sys_user u ON u.id = cc.user_id AND u.is_deleted = 0
            WHERE cc.is_deleted = 0 AND cc.culture_id = #{cultureId} AND cc.parent_id = 0
            ORDER BY cc.create_time DESC, cc.id DESC
            """)
    IPage<CultureCommentVO> selectTopPage(IPage<CultureCommentVO> page, @Param("cultureId") Long cultureId);

    /** 查询顶级评论下的全部二级回复：联表取评论者与被回复人昵称，按时间正序（对话顺序） */
    @Select("""
            <script>
            SELECT cc.id, cc.culture_id, cc.user_id, cc.parent_id, cc.reply_user_id,
                   cc.comment_content, cc.create_time,
                   u.nickname, u.avatar, u.role AS user_role,
                   ru.nickname AS reply_nickname
            FROM culture_comment cc
            LEFT JOIN sys_user u ON u.id = cc.user_id AND u.is_deleted = 0
            LEFT JOIN sys_user ru ON ru.id = cc.reply_user_id AND ru.is_deleted = 0
            WHERE cc.is_deleted = 0 AND cc.parent_id IN
            <foreach collection="parentIds" item="parentId" open="(" separator="," close=")">
                #{parentId}
            </foreach>
            ORDER BY cc.create_time ASC, cc.id ASC
            </script>
            """)
    List<CultureCommentVO> selectRepliesByParentIds(@Param("parentIds") List<Long> parentIds);

    /** 按ID查询单条评论VO（发布成功后返回，联表回填昵称头像与被回复人昵称） */
    @Select("""
            SELECT cc.id, cc.culture_id, cc.user_id, cc.parent_id, cc.reply_user_id,
                   cc.comment_content, cc.create_time,
                   u.nickname, u.avatar, u.role AS user_role,
                   ru.nickname AS reply_nickname
            FROM culture_comment cc
            LEFT JOIN sys_user u ON u.id = cc.user_id AND u.is_deleted = 0
            LEFT JOIN sys_user ru ON ru.id = cc.reply_user_id AND ru.is_deleted = 0
            WHERE cc.id = #{id} AND cc.is_deleted = 0
            """)
    CultureCommentVO selectVOById(@Param("id") Long id);

    /** 连带逻辑删除某顶级评论下的全部二级回复（绕过 @TableLogic 自动过滤） */
    @Update("UPDATE culture_comment SET is_deleted = 1 WHERE parent_id = #{parentId} AND is_deleted = 0")
    int updateDeletedByParentId(@Param("parentId") Long parentId);
}
