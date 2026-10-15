package com.mic.inheritReserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.inheritReserve.model.entity.UserWatchRecord;
import com.mic.inheritReserve.model.vo.MyWatchVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 学习视频观看记录表Mapper
 */
public interface UserWatchRecordMapper extends BaseMapper<UserWatchRecord> {

    /**
     * 记录完整观看（幂等）：唯一键 uk_user_video(user_id, video_id) 命中时仅刷新最近观看时间，
     * 历史逻辑删除记录一并复活（is_deleted 置 0），避免重复完整观看产生多条记录
     */
    @Insert("INSERT INTO user_watch_record (user_id, video_id) VALUES (#{userId}, #{videoId}) "
            + "ON DUPLICATE KEY UPDATE update_time = NOW(), is_deleted = 0")
    int upsertWatch(@Param("userId") Long userId, @Param("videoId") Long videoId);

    /**
     * 我学习的视频分页：仅统计仍有效（未删除）且审核已通过（audit_status=1）的视频，
     * 按最近观看时间倒序（下架 / 驳回视频不再出现在学习记录中）
     * 说明：自定义 SQL 不经过 @TableLogic 拦截，需手动过滤 is_deleted = 0
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param userId 当前登录用户ID（非遗人 / 普通用户）
     */
    @Select("""
            SELECT v.id AS video_id, v.title, v.content, v.img_url, v.video_url, v.duration, w.update_time AS watch_time
            FROM user_watch_record w
            INNER JOIN intangible_video v ON v.id = w.video_id AND v.is_deleted = 0 AND v.audit_status = 1
            WHERE w.user_id = #{userId} AND w.is_deleted = 0
            ORDER BY w.update_time DESC, w.id DESC
            """)
    IPage<MyWatchVO> selectMyWatchPage(IPage<MyWatchVO> page, @Param("userId") Long userId);
}
