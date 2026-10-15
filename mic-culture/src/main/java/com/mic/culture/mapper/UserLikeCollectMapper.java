package com.mic.culture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.culture.model.entity.IntangibleCulture;
import com.mic.culture.model.entity.UserLikeCollect;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 点赞收藏表Mapper
 * 说明：有效状态通过 is_deleted 切换（0有效 / 1取消），同一（用户+资料+类型）仅保留一条物理记录，
 * 避免唯一键 uk_user_culture_type 在反复点赞 / 取消时冲突；自定义SQL不受 @TableLogic 自动过滤，条件显式声明
 */
public interface UserLikeCollectMapper extends BaseMapper<UserLikeCollect> {

    /**
     * 查询点赞收藏记录（含已取消记录）
     * 取消后再次点赞时恢复原记录，规避唯一键冲突
     */
    @Select("SELECT * FROM user_like_collect WHERE user_id = #{userId} AND culture_id = #{cultureId} AND type = #{type} LIMIT 1")
    UserLikeCollect selectIncludingDeleted(@Param("userId") Long userId,
                                           @Param("cultureId") Long cultureId,
                                           @Param("type") Integer type);

    /** 切换逻辑删除标记（0恢复 / 1取消）：自定义SQL绕过 @TableLogic 自动过滤 */
    @Update("UPDATE user_like_collect SET is_deleted = #{isDeleted} WHERE id = #{id}")
    int updateDeletedById(@Param("id") Long id, @Param("isDeleted") Integer isDeleted);

    /** 查询当前用户对指定资料的有效记录（type 1点赞 / 2收藏），供详情页状态回显 */
    @Select("SELECT * FROM user_like_collect WHERE user_id = #{userId} AND culture_id = #{cultureId} AND is_deleted = 0")
    List<UserLikeCollect> selectActiveByUserAndCulture(@Param("userId") Long userId,
                                                       @Param("cultureId") Long cultureId);

    /**
     * 我的点赞 / 收藏分页：连表查询仍有效的已发布资料（资料被删除 / 未发布时自动排除），按操作时间倒序
     */
    @Select("""
            SELECT c.* FROM user_like_collect lc
            INNER JOIN intangible_culture c
                ON c.id = lc.culture_id AND c.is_deleted = 0 AND c.status = 1
            WHERE lc.user_id = #{userId} AND lc.type = #{type} AND lc.is_deleted = 0
            ORDER BY lc.create_time DESC, lc.id DESC
            """)
    IPage<IntangibleCulture> selectMyPage(IPage<IntangibleCulture> page,
                                          @Param("userId") Long userId,
                                          @Param("type") Integer type);
}
