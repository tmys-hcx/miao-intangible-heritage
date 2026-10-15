package com.mic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.goods.model.dto.GoodsCategorySaveDTO;
import com.mic.goods.model.entity.GoodsCategory;

import java.util.List;

/**
 * 商品分类服务接口
 */
public interface GoodsCategoryService extends IService<GoodsCategory> {

    /** 分类列表：parentId为空查全部，传0查一级分类，传具体ID查其子分类（按父级、排序号升序） */
    List<GoodsCategory> listCategories(Long parentId);

    /**
     * 关键词匹配的商品分类ID集合（商品搜索使用）：分类名称模糊匹配一级 / 二级分类
     * 命中一级分类：返回该分类及其全部二级子分类的ID（搜一级分类名可查到子分类商品）；
     * 命中二级分类：返回该二级分类ID；未命中返回空集合
     * @param keyword 搜索关键词（null / 空时返回空集合）
     */
    List<Long> matchCategoryIdsByKeyword(String keyword);

    /**
     * 分页查询一级分类（含二级子分类），按创建时间倒序
     * @param pageDto 分页参数（默认第1页、每页10条）
     * @param categoryName 分类名称模糊查询条件（可选，空则不限制）
     * @return 分页结果，records 为一级分类（携带 children 二级子分类）
     */
    PageResult<GoodsCategory> pageCategories(PageDto pageDto, String categoryName);

    /** 新增分类（校验父分类有效性） */
    void saveCategory(GoodsCategorySaveDTO dto);

    /** 修改分类（校验分类存在、父分类有效性、禁止将自身设为父级） */
    void updateCategory(Long id, GoodsCategorySaveDTO dto);

    /** 删除分类（物理删除，存在子分类时禁止删除） */
    void deleteCategory(Long id);
}
