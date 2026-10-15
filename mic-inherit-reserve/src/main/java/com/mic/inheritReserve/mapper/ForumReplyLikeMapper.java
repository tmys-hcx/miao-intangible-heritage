package com.mic.inheritReserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mic.inheritReserve.model.entity.ForumReplyLike;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 论坛评论点赞表Mapper
 * 说明：有效状态通过 is_deleted 切换（0有效 / 1取消），同一（用户+评论）仅保留一条物理记录，
 * 避免唯一键 uk_user_reply 在反复点赞 / 取消时冲突；自定义SQL不受 @TableLogic 自动过滤，条件显式声明
 */
public interface ForumReplyLikeMapper extends BaseMapper<ForumReplyLike> {

    /**
     * 查询点赞记录（含已取消记录）
     * 取消后再次点赞时恢复原记录，规避唯一键冲突
     */
    @Select("SELECT * FROM inherit_forum_reply_like WHERE user_id = #{userId} AND reply_id = #{replyId} LIMIT 1")
    ForumReplyLike selectIncludingDeleted(@Param("userId") Long userId, @Param("replyId") Long replyId);

    /** 切换逻辑删除标记（0恢复 / 1取消）：自定义SQL绕过 @TableLogic 自动过滤 */
    @Update("UPDATE inherit_forum_reply_like SET is_deleted = #{isDeleted} WHERE id = #{id}")
    int updateDeletedById(@Param("id") Long id, @Param("isDeleted") Integer isDeleted);

    /** 批量查询当前用户在指定评论集合下的有效点赞评论ID（评论列表点赞状态回显；服务层判空防 IN () 语法错误） */
    @Select("""
            <script>
            SELECT reply_id FROM inherit_forum_reply_like
            WHERE user_id = #{userId} AND is_deleted = 0 AND reply_id IN
            <foreach collection="replyIds" item="replyId" open="(" separator="," close=")">
                #{replyId}
            </foreach>
            </script>
            """)
    List<Long> selectLikedReplyIds(@Param("userId") Long userId, @Param("replyIds") List<Long> replyIds);
}
