package com.mic.common.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 统计趋势点VO（管理端首页数据统计使用）：
 * 各业务服务按「近7天每日数量」聚合后返回（日期升序，无数据的日期补0），
 * 前端 ECharts 折线图直接以 date 为横轴、count 为纵轴渲染
 */
@Data
public class StatsTrendVO implements Serializable {

    /** 日期标签（MM-dd，如 09-20） */
    private String date;

    /** 当日数量（新增用户 / 新增资料 / 新增订单等） */
    private Long count;

    public StatsTrendVO() {
    }

    public StatsTrendVO(String date, Long count) {
        this.date = date;
        this.count = count;
    }
}
