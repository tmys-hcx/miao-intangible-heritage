package com.mic.user.model.vo;

import com.mic.common.model.vo.StatsTrendVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 管理端首页数据统计VO（用户域）：
 * 用户总量 / 今日新增 / 角色分布 / 待审核非遗人申请数 / 近7天注册趋势，
 * 供管理端「数据统计」页数字卡片与图表聚合展示
 */
@Data
public class AdminStatsVO implements Serializable {

    /** 用户总数（不含逻辑删除） */
    private Long userTotal;

    /** 今日新增用户数 */
    private Long todayNewUsers;

    /** 管理员数量（role=0） */
    private Long adminCount;

    /** 非遗人数量（role=1） */
    private Long heritageCount;

    /** 普通用户数量（role=2） */
    private Long normalCount;

    /** 待审核非遗人申请数（heritage_apply.audit_status=0） */
    private Long pendingApplies;

    /** 近7天注册趋势（含今天，日期升序，无数据补0） */
    private List<StatsTrendVO> registerTrend;
}
