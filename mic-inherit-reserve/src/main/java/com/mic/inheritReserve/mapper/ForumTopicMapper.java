package com.mic.inheritReserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mic.inheritReserve.model.entity.ForumTopic;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 论坛话题Mapper
 */
public interface ForumTopicMapper extends BaseMapper<ForumTopic> {

    /**
     * 话题列表：按使用次数倒序（同次数按创建先后），供发布卡片「#」浮层选择
     * 说明：自定义 SQL 不经过 @TableLogic 拦截，需手动过滤 is_deleted = 0
     */
    @Select("SELECT * FROM inherit_forum_topic WHERE is_deleted = 0 ORDER BY use_count DESC, id ASC")
    List<ForumTopic> selectHotList();

    /**
     * 话题使用次数增减（原子更新，最低不小于 0）：
     * 发布选用话题 +1、编辑换话题（旧 -1 新 +1）、删除帖子 -1
     * @param id    话题ID
     * @param delta 增减值（+1 / -1）
     */
    @Update("UPDATE inherit_forum_topic SET use_count = GREATEST(use_count + #{delta}, 0) "
            + "WHERE id = #{id} AND is_deleted = 0")
    int updateUseCount(@Param("id") Long id, @Param("delta") int delta);
}
