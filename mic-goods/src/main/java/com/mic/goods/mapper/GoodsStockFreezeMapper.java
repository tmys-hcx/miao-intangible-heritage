package com.mic.goods.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mic.goods.model.entity.GoodsStockFreeze;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 库存冻结流水Mapper（TCC 预扣库存）
 */
public interface GoodsStockFreezeMapper extends BaseMapper<GoodsStockFreeze> {

    /**
     * 条件更新冻结状态（二阶段幂等核心）：仅命中预期前置状态的记录才执行，
     * Confirm 重复投递时 T→C 影响行数为 0，据此跳过重复落库
     * @return 影响行数，0 表示状态已终结（已提交 / 已取消 / 空回滚后重复 Confirm）
     */
    @Update("UPDATE goods_stock_freeze SET status = #{toStatus} WHERE order_no = #{orderNo} AND goods_id = #{goodsId} AND status = #{fromStatus}")
    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("goodsId") Long goodsId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);

    /**
     * 单商品未终结（T）冻结总量：商品库存变更重置可用池、启动预热回算 avail 时扣除在途冻结
     */
    @Select("SELECT COALESCE(SUM(num), 0) FROM goods_stock_freeze WHERE goods_id = #{goodsId} AND status = 'T'")
    int sumPendingFrozen(@Param("goodsId") Long goodsId);

    /**
     * 全部商品未终结（T）冻结总量（按商品分组）：启动预热一次性加载，避免逐商品查询
     * @return 每行 {goods_id, frozen_num}
     */
    @Select("SELECT goods_id, SUM(num) AS frozen_num FROM goods_stock_freeze WHERE status = 'T' GROUP BY goods_id")
    List<Map<String, Object>> listAllPendingFrozen();
}
