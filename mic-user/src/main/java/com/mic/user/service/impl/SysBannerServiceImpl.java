package com.mic.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.user.mapper.SysBannerMapper;
import com.mic.user.model.entity.SysBanner;
import com.mic.user.service.SysBannerService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 轮播图服务实现
 */
@Service
public class SysBannerServiceImpl extends ServiceImpl<SysBannerMapper, SysBanner> implements SysBannerService {

    @Override
    public PageResult<SysBanner> pageBanners(PageDto pageDto, String keyword) {
        Page<SysBanner> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        // 逻辑删除自动过滤（is_deleted=0），按排序号升序展示；keyword 可选对描述模糊搜索
        lambdaQuery()
                .like(StringUtils.hasText(keyword), SysBanner::getBannerDesc, keyword)
                .orderByAsc(SysBanner::getSort)
                .orderByAsc(SysBanner::getId)
                .page(page);
        return PageResult.of(page);
    }

    @Override
    public void saveBanner(SysBanner banner) {
        if (banner.getBannerType() == null) {
            banner.setBannerType(SysBanner.TYPE_IMAGE);
        }
        // id 由数据库自增，创建 / 更新时间由 MyMetaObjectHandler 自动填充
        banner.setId(null);
        save(banner);
    }

    @Override
    public void updateBanner(SysBanner banner) {
        if (banner.getId() == null || getById(banner.getId()) == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "轮播图不存在或已被删除");
        }
        // null 字段不更新（MyBatis-Plus 默认策略），updateTime 自动填充
        updateById(banner);
    }

    @Override
    public void removeBanner(Long id) {
        if (id == null || getById(id) == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "轮播图不存在或已被删除");
        }
        removeById(id);
    }
}
