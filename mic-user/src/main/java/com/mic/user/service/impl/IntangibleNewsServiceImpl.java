package com.mic.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.user.mapper.IntangibleNewsMapper;
import com.mic.user.model.entity.IntangibleNews;
import com.mic.user.model.vo.SysNewsVO;
import com.mic.user.service.IntangibleNewsService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资讯公告服务实现
 */
@Service
public class IntangibleNewsServiceImpl extends ServiceImpl<IntangibleNewsMapper, IntangibleNews>
        implements IntangibleNewsService {

    @Override
    public PageResult<SysNewsVO> pageNews(PageDto pageDto) {
        Page<IntangibleNews> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        // 逻辑删除自动过滤（is_deleted=0），按发布时间倒序展示
        lambdaQuery().orderByDesc(IntangibleNews::getCreateTime).page(page);

        List<SysNewsVO> records = page.getRecords().stream().map(this::toNewsVO).toList();
        return PageResult.of(page, records);
    }

    /** 实体转资讯VO（对前台仅暴露必要字段） */
    private SysNewsVO toNewsVO(IntangibleNews news) {
        SysNewsVO vo = new SysNewsVO();
        vo.setId(news.getId());
        vo.setTitle(news.getTitle());
        vo.setContent(news.getContent());
        vo.setCreateTime(news.getCreateTime());
        return vo;
    }
}
