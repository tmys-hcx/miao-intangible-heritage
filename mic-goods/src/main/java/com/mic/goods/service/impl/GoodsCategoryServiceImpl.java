package com.mic.goods.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.mapper.GoodsCategoryMapper;
import com.mic.goods.model.dto.GoodsCategorySaveDTO;
import com.mic.goods.model.entity.GoodsCategory;
import com.mic.goods.service.GoodsCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品分类服务实现
 */
@Slf4j
@Service
public class GoodsCategoryServiceImpl extends ServiceImpl<GoodsCategoryMapper, GoodsCategory> implements GoodsCategoryService {

    /**
     * 根据父分类ID获取子分类列表
     * @param parentId 父分类ID
     * @return
     */
    @Override
    public List<GoodsCategory> listCategories(Long parentId) {
        // 逻辑删除自动过滤（is_deleted=0）；parentId为空返回全部，便于前端按层级组织树形结构
        return lambdaQuery()
                .eq(parentId != null, GoodsCategory::getParentId, parentId)
                .orderByAsc(GoodsCategory::getParentId)
                .orderByAsc(GoodsCategory::getSort)
                .list();
    }

    /**
     * 关键词匹配的商品分类ID集合：分类名称模糊匹配（一级 / 二级分类一起查）
     * 一级分类命中：带出其全部二级子分类（商品搜索「输入一级分类名可查到子分类商品」）
     */
    @Override
    public List<Long> matchCategoryIdsByKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        List<GoodsCategory> matched = lambdaQuery()
                .like(GoodsCategory::getCategoryName, keyword.trim())
                .list();
        if (matched.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = new HashSet<>();
        Set<Long> rootIds = new HashSet<>();
        for (GoodsCategory category : matched) {
            categoryIds.add(category.getId());
            if (category.getParentId() == null || category.getParentId() == 0L) {
                rootIds.add(category.getId());
            }
        }
        // 一级分类命中：补充其全部二级子分类ID（子分类下的商品同样命中）
        if (!rootIds.isEmpty()) {
            lambdaQuery()
                    .in(GoodsCategory::getParentId, rootIds)
                    .list()
                    .forEach(child -> categoryIds.add(child.getId()));
        }
        return List.copyOf(categoryIds);
    }

    /**
     * 分页查询一级分类（含二级子分类），按创建时间倒序
     */
    @Override
    public PageResult<GoodsCategory> pageCategories(PageDto pageDto, String categoryName) {
        String keyword = categoryName == null ? null : categoryName.trim();
        if (keyword != null && keyword.isEmpty()) {
            keyword = null;
        }

        Page<GoodsCategory> page = new Page<>(pageDto.getCurrent(), pageDto.getSize());
        if (keyword == null) {
            // 无查询条件：一级分类按创建时间倒序（同一时间按ID升序稳定排序）
            lambdaQuery()
                    .eq(GoodsCategory::getParentId, 0L)
                    .orderByDesc(GoodsCategory::getCreateTime)
                    .orderByAsc(GoodsCategory::getId)
                    .page(page);
            fillChildren(page.getRecords(), null, null);
            return PageResult.of(page);
        }

        // 模糊匹配分类名称：一级、二级分类一起查
        List<GoodsCategory> matched = lambdaQuery()
                .like(GoodsCategory::getCategoryName, keyword)
                .list();
        if (matched.isEmpty()) {
            return PageResult.of(page);
        }
        // 命中的一级分类ID：一级自身命中 / 二级命中带出所属父级，两种情况汇总去重
        Set<Long> matchedRootIds = matched.stream()
                .map(item -> item.getParentId() == 0L ? item.getId() : item.getParentId())
                .collect(Collectors.toSet());
        // 一级分类自身命中的ID集合：这些分类的 children 保留全部二级
        Set<Long> selfMatchedRootIds = matched.stream()
                .filter(item -> item.getParentId() == 0L)
                .map(GoodsCategory::getId)
                .collect(Collectors.toSet());

        lambdaQuery()
                .in(GoodsCategory::getId, matchedRootIds)
                .orderByDesc(GoodsCategory::getCreateTime)
                .orderByAsc(GoodsCategory::getId)
                .page(page);
        fillChildren(page.getRecords(), keyword, selfMatchedRootIds);
        return PageResult.of(page);
    }

    /**
     * 为一级分类批量装配二级子分类（一次查询，避免逐条查库）
     * @param roots 一级分类列表
     * @param keyword 查询关键词：非空时，“仅二级命中带出”的一级分类只保留命中的子级
     * @param selfMatchedRootIds 一级分类自身命中的ID集合（无关键词时传null，表示保留全部子级）
     */
    private void fillChildren(List<GoodsCategory> roots, String keyword, Set<Long> selfMatchedRootIds) {
        if (roots == null || roots.isEmpty()) {
            return;
        }
        List<Long> rootIds = roots.stream().map(GoodsCategory::getId).toList();
        List<GoodsCategory> allChildren = lambdaQuery()
                .in(GoodsCategory::getParentId, rootIds)
                .orderByAsc(GoodsCategory::getSort)
                .orderByAsc(GoodsCategory::getId)
                .list();
        Map<Long, List<GoodsCategory>> childrenMap = allChildren.stream()
                .collect(Collectors.groupingBy(GoodsCategory::getParentId));
        String kw = keyword == null ? null : keyword.toLowerCase();
        for (GoodsCategory root : roots) {
            List<GoodsCategory> children = childrenMap.getOrDefault(root.getId(), List.of());
            // 无关键词，或一级分类自身命中：保留全部子级；二级命中带出的父级：只保留命中的子级
            if (kw != null && (selfMatchedRootIds == null || !selfMatchedRootIds.contains(root.getId()))) {
                children = children.stream()
                        .filter(child -> child.getCategoryName().toLowerCase().contains(kw))
                        .toList();
            }
            root.setChildren(children.isEmpty() ? null : children);
        }
    }

    /**
     * 新增商品分类
     * @param dto
     */
    @Override
    public void saveCategory(GoodsCategorySaveDTO dto) {
        checkParent(dto.getParentId(), null);

        GoodsCategory category = new GoodsCategory();
        category.setCategoryName(dto.getCategoryName());
        category.setCategoryDesc(dto.getCategoryDesc());
        category.setParentId(dto.getParentId());
        category.setSort(dto.getSort());
        save(category);
        log.info("新增商品分类成功：id={}, categoryName={}", category.getId(), category.getCategoryName());
    }

    /**
     * 修改商品分类
     * @param id
     * @param dto
     */
    @Override
    public void updateCategory(Long id, GoodsCategorySaveDTO dto) {
        if (getById(id) == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品分类不存在");
        }
        checkParent(dto.getParentId(), id);

        GoodsCategory category = new GoodsCategory();
        category.setId(id);
        category.setCategoryName(dto.getCategoryName());
        category.setCategoryDesc(dto.getCategoryDesc());
        category.setParentId(dto.getParentId());
        category.setSort(dto.getSort());
        updateById(category);
        log.info("修改商品分类成功：id={}", id);
    }

    /**
     * 删除商品分类
     * @param id
     */
    @Override
    public void deleteCategory(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "商品分类不存在");
        }
        // 存在子分类时禁止删除，避免产生孤儿数据
        if (lambdaQuery().eq(GoodsCategory::getParentId, id).exists()) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "该分类下存在子分类，请先删除子分类");
        }
        // 物理删除：直接删除数据库记录（分类由管理员维护、数据量小，按需求不做逻辑删除）
        baseMapper.deletePhysicsById(id);
        log.info("删除商品分类成功（物理删除）：id={}", id);
    }

    /** 父分类有效性：parentId=0 为一级分类；大于0时须是已存在分类，且不能将自身设为父级 */
    private void checkParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "不能将自身设为父分类");
        }
        if (getById(parentId) == null) {
            throw new BusinessException(ResultCodeEnum.PARAM_ERROR, "父分类不存在");
        }
    }
}
