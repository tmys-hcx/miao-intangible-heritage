package com.mic.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mic.goods.model.entity.GoodsCategory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 商品分类表Mapper
 */
public interface GoodsCategoryMapper extends BaseMapper<GoodsCategory> {

    /**
     * 物理删除分类记录（DELETE，绕过 @TableLogic 逻辑删除）
     * 说明：商品分类数据量小且仅由管理员维护，按需求采用物理删除
     */
    @Delete("DELETE FROM goods_category WHERE id = #{id}")
    int deletePhysicsById(@Param("id") Long id);
}
