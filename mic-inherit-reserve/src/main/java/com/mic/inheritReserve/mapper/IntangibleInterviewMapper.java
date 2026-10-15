package com.mic.inheritReserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.inheritReserve.model.entity.IntangibleInterview;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 访谈资料表Mapper
 */
public interface IntangibleInterviewMapper extends BaseMapper<IntangibleInterview> {

    /**
     * 管理端分页（含发布人账号）：LEFT JOIN sys_user（同库）关联发布人账号，填充瞬态字段 publisherAccount（账号缺失时为 null）
     * 说明：自定义 SQL 不经过 @TableLogic 拦截，需手动过滤 is_deleted = 0
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param keyword 标题关键词（模糊匹配），null/空=不过滤
     */
    @Select("""
            <script>
            SELECT i.*, u.account AS publisher_account
            FROM intangible_interview i
            LEFT JOIN sys_user u ON u.id = i.publisher_id AND u.is_deleted = 0
            WHERE i.is_deleted = 0
            <if test="keyword != null and keyword != ''">
                AND i.title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY i.create_time DESC, i.id DESC
            </script>
            """)
    IPage<IntangibleInterview> selectAdminPage(IPage<IntangibleInterview> page,
                                               @Param("keyword") String keyword);
}
