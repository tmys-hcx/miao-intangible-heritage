package com.mic.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.user.model.entity.IntangibleNews;
import com.mic.user.model.vo.SysNewsVO;

/**
 * 资讯公告服务接口
 */
public interface IntangibleNewsService extends IService<IntangibleNews> {

    /** 资讯公告分页（免登录，按发布时间倒序） */
    PageResult<SysNewsVO> pageNews(PageDto pageDto);
}
