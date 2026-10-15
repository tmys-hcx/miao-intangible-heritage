package com.mic.goods.model.vo;

import com.mic.common.model.vo.StatsStatusCountVO;
import com.mic.common.model.vo.StatsTrendVO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端首页数据统计VO（非遗商品域）：
 * 商品总量与状态分布 / 订单总量与状态分布 / 成交金额（已支付口径）/ 近7天订单趋势，
 * 供管理端「数据统计」页数字卡片与图表聚合展示
 */
@Data
public class AdminStatsVO implements Serializable {

    /** 商品总数（不含逻辑删除） */
    private Long goodsTotal;

    /** 待审核商品数（status=0） */
    private Long goodsPending;

    /** 上架商品数（status=1） */
    private Long goodsOnSale;

    /** 打回商品数（status=2） */
    private Long goodsRejected;

    /** 已下架商品数（status=3） */
    private Long goodsOffShelf;

    /** 订单总数（不含逻辑删除） */
    private Long orderTotal;

    /** 成交金额：已支付口径（排除 0待付款 4已退款 5已取消 8退款申请中）的订单金额合计 */
    private BigDecimal paidAmount;

    /** 订单各状态数量（0待付款~8退款申请中，status+count 列表） */
    private List<StatsStatusCountVO> orderStatusCounts;

    /** 近7天订单趋势（含今天，日期升序，无数据补0） */
    private List<StatsTrendVO> orderTrend;
}
