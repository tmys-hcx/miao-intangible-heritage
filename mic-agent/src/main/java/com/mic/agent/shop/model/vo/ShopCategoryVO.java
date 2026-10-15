package com.mic.agent.shop.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 商品分类VO（Agent 侧契约类）：
 * 与 mic-goods 商品分类接口返回的 GoodsCategory 字段对齐，
 * children 为二级子分类（分类分页接口组装返回）
 */
@Data
public class ShopCategoryVO implements Serializable {

    /** 分类ID（查询商品列表时可作为 categoryId 传参） */
    private Long id;

    /** 分类名称 */
    private String categoryName;

    /** 分类描述 */
    private String categoryDesc;

    /** 父分类ID（一级分类为0） */
    private Long parentId;

    /** 二级子分类列表 */
    private List<ShopCategoryVO> children;
}
