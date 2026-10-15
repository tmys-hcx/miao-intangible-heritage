package com.mic.inheritReserve.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.inheritReserve.model.entity.IntangibleVideo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 学习视频表Mapper
 */
public interface IntangibleVideoMapper extends BaseMapper<IntangibleVideo> {

    /**
     * 管理端分页（含发布人账号）：LEFT JOIN sys_user（同库）关联发布人账号，填充瞬态字段 publisherAccount（账号缺失时为 null）
     * 说明：自定义 SQL 不经过 @TableLogic 拦截，需手动过滤 is_deleted = 0
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param keyword 标题关键词（模糊匹配），null/空=不过滤
     * @param auditStatus 审核状态筛选（0待审核 1已通过 2已驳回），null=全部
     */
    @Select("""
            <script>
            SELECT v.*, u.account AS publisher_account
            FROM intangible_video v
            LEFT JOIN sys_user u ON u.id = v.publisher_id AND u.is_deleted = 0
            WHERE v.is_deleted = 0
            <if test="keyword != null and keyword != ''">
                AND v.title LIKE CONCAT('%', #{keyword}, '%')
            </if>
            <if test="auditStatus != null">
                AND v.audit_status = #{auditStatus}
            </if>
            ORDER BY v.create_time DESC, v.id DESC
            </script>
            """)
    IPage<IntangibleVideo> selectAdminPage(IPage<IntangibleVideo> page,
                                           @Param("keyword") String keyword,
                                           @Param("auditStatus") Integer auditStatus);
}
