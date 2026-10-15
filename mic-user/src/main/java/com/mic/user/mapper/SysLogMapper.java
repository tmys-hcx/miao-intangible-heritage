package com.mic.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.user.model.entity.SysLog;
import com.mic.user.model.vo.SysLogVO;
import org.apache.ibatis.annotations.Param;

/**
 * 系统日志表Mapper
 */
public interface SysLogMapper extends BaseMapper<SysLog> {

    /**
     * 系统日志分页（管理端系统日志）：全部操作日志（按操作时间倒序），
     * 支持操作模块 / 操作人账号模糊搜索
     * LEFT JOIN sys_user（同库）关联操作人账号，填充 userAccount（账号缺失时为 null）
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param keyword 搜索关键词（null/空=不筛选；匹配操作模块或操作人账号）
     */
    IPage<SysLogVO> selectLogPage(IPage<SysLogVO> page, @Param("keyword") String keyword);
}
