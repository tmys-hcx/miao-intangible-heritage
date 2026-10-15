package com.mic.common.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 状态计数VO（管理端首页数据统计使用）：
 * 业务数据按状态字段分组统计的通用结构（如订单各状态数量、内容各审核状态数量），
 * 前端 ECharts 环形图 / 柱状图直接映射状态文案与数量渲染
 */
@Data
public class StatsStatusCountVO implements Serializable {

    /** 状态值（含义由各业务表约定，如订单 0待付款~8退款申请中） */
    private Integer status;

    /** 该状态下的记录数 */
    private Long count;

    public StatsStatusCountVO() {
    }

    public StatsStatusCountVO(Integer status, Long count) {
        this.status = status;
        this.count = count;
    }
}
