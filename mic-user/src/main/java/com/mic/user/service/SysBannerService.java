package com.mic.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.user.model.entity.SysBanner;

/**
 * 轮播图服务接口
 */
public interface SysBannerService extends IService<SysBanner> {

    /** 轮播图分页（免登录，按sort升序；keyword 可选按描述模糊搜索） */
    PageResult<SysBanner> pageBanners(PageDto pageDto, String keyword);

    /** 新增轮播图（管理员）：bannerType 兜底为图片类型 */
    void saveBanner(SysBanner banner);

    /** 修改轮播图（管理员）：id 必传，null 字段不更新 */
    void updateBanner(SysBanner banner);

    /** 逻辑删除轮播图（管理员） */
    void removeBanner(Long id);
}
