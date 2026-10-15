package com.mic.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.goods.model.entity.GoodsEvaluate;
import org.apache.ibatis.annotations.Param;

/**
 * 商品评价表Mapper
 */
public interface GoodsEvaluateMapper extends BaseMapper<GoodsEvaluate> {

    /**
     * 商品评价分页（详情页展示区）：LEFT JOIN sys_user（同库）填充评价人昵称
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param goodsId 商品ID筛选（null=查全部评价）
     */
    IPage<GoodsEvaluate> selectEvaluatePage(IPage<GoodsEvaluate> page, @Param("goodsId") Long goodsId);
}
