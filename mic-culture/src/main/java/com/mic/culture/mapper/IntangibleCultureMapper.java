package com.mic.culture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.culture.model.entity.IntangibleCulture;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 非遗文化资料表Mapper
 */
public interface IntangibleCultureMapper extends BaseMapper<IntangibleCulture> {

    /**
     * 后台资料分页（管理端审核列表）：全部资料（含待审核 / 已驳回），可按审核状态筛选
     * LEFT JOIN sys_user（同库）关联发布人账号，填充瞬态字段 publisherAccount（账号缺失时为 null）
     * 说明：自定义 SQL 不经过 @TableLogic 拦截，需手动过滤 is_deleted = 0
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param auditStatus 审核状态筛选（null=全部：0待审核 1已发布 2已驳回）
     */
    @Select("""
            <script>
            SELECT c.*, u.account AS publisher_account
            FROM intangible_culture c
            LEFT JOIN sys_user u ON u.id = c.publisher_id AND u.is_deleted = 0
            WHERE c.is_deleted = 0
            <if test="auditStatus != null">
                AND c.status = #{auditStatus}
            </if>
            ORDER BY c.create_time DESC, c.id DESC
            </script>
            """)
    IPage<IntangibleCulture> selectAdminCulturePage(IPage<IntangibleCulture> page,
                                                    @Param("auditStatus") Integer auditStatus);
}
