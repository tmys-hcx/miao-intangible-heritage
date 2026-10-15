package com.mic.culture.model.vo;

import com.mic.common.model.vo.StatsTrendVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 管理端首页数据统计VO（非遗文化域）：
 * 资料总量 / 状态分布（待审核·已发布·已驳回）/ 总浏览量 / 近7天发布趋势，
 * 供管理端「数据统计」页数字卡片与图表聚合展示
 */
@Data
public class AdminStatsVO implements Serializable {

    /** 资料总数（不含逻辑删除） */
    private Long cultureTotal;

    /** 待审核数（status=0） */
    private Long pendingCount;

    /** 已发布数（status=1） */
    private Long publishedCount;

    /** 已驳回数（status=2） */
    private Long rejectedCount;

    /** 总浏览量（SUM(view_count)） */
    private Long totalViewCount;

    /** 近7天发布趋势（含今天，日期升序，无数据补0） */
    private List<StatsTrendVO> publishTrend;
}
