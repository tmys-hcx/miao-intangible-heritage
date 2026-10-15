package com.mic.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mic.goods.model.entity.IntangibleGoods;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 非遗商品表Mapper
 */
public interface IntangibleGoodsMapper extends BaseMapper<IntangibleGoods> {

    /**
     * 前台商品分页：仅已审核（status=1）商品，支持分类筛选 / 名称、描述或分类名模糊搜索 / 最新·最热排序
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param categoryId 分类ID（null=全部分类）
     * @param keyword 搜索关键词（null/空=不筛选；匹配商品名称或描述）
     * @param categoryIds 关键词命中的分类ID集合（服务层解析，null/空=不追加分类条件；
     *                    一级分类名命中时已带出其全部二级子分类ID，与 keyword 为 OR 关系）
     * @param hot 是否最热排序（true=按销量即订单数倒序；false=按创建时间倒序）
     */
    IPage<IntangibleGoods> selectGoodsPage(IPage<IntangibleGoods> page,
                                           @Param("categoryId") Long categoryId,
                                           @Param("keyword") String keyword,
                                           @Param("categoryIds") List<Long> categoryIds,
                                           @Param("hot") boolean hot);

    /**
     * 前台最新上架商品推荐（不分页）：仅已审核（status=1）商品，按发布时间倒序取前 N 条
     * 支持分类筛选 / 名称、描述或分类名模糊搜索
     * @param categoryId 分类ID（null=全部分类）
     * @param keyword 搜索关键词（null/空=不筛选；匹配商品名称或描述）
     * @param categoryIds 关键词命中的分类ID集合（服务层解析，null/空=不追加分类条件；
     *                    一级分类名命中时已带出其全部二级子分类ID，与 keyword 为 OR 关系）
     * @param limit 返回条数上限（前台商品推荐固定取9条）
     */
    List<IntangibleGoods> selectLatestGoods(@Param("categoryId") Long categoryId,
                                            @Param("keyword") String keyword,
                                            @Param("categoryIds") List<Long> categoryIds,
                                            @Param("limit") int limit);

    /**
     * 后台商品分页（管理端审核列表）：全部商品（含待审核 / 已拒绝），可按审核状态筛选
     * LEFT JOIN sys_user（同库）关联发布人账号，填充瞬态字段 publisherAccount（账号缺失时为 null）
     * @param page MyBatis-Plus 分页对象（由分页插件自动分页与统计总数）
     * @param auditStatus 审核状态筛选（null=全部：0待审核 1审核通过 2审核拒绝）
     */
    IPage<IntangibleGoods> selectAdminGoodsPage(IPage<IntangibleGoods> page,
                                                @Param("auditStatus") Integer auditStatus);

    /**
     * 原子扣减库存（创建订单时调用）：自定义 SQL 需手动过滤 is_deleted
     * @return 影响行数，0 表示库存不足或商品不存在/已删除
     */
    @Update("UPDATE intangible_goods SET stock = stock - #{num} WHERE id = #{id} AND is_deleted = 0 AND stock >= #{num}")
    int deductStock(@Param("id") Long id, @Param("num") int num);

    /**
     * 库存售罄自动下架（下单扣减库存后调用）：仅命中「已上架且库存为 0」的商品，
     * 条件更新保证并发下多单时仅将库存扣到 0 的那单触发下架
     * @return 影响行数，0 表示库存仍有余 / 非上架状态 / 商品不存在
     */
    @Update("UPDATE intangible_goods SET status = 3 WHERE id = #{id} AND is_deleted = 0 AND status = 1 AND stock <= 0")
    int offShelfIfSoldOut(@Param("id") Long id);

    /**
     * 恢复库存（订单退款成功时调用）
     * @return 影响行数，商品不存在/已删除时为 0
     */
    @Update("UPDATE intangible_goods SET stock = stock + #{num} WHERE id = #{id} AND is_deleted = 0")
    int restoreStock(@Param("id") Long id, @Param("num") int num);
}
