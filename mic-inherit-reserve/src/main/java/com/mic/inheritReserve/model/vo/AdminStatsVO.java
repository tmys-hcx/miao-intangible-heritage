package com.mic.inheritReserve.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端首页数据统计VO（传承与学习域）：
 * 论坛帖子总量 / 待审核帖子数 / 回复总量 / 学习视频总量 / 待审核视频数，
 * 供管理端「数据统计」页数字卡片展示
 */
@Data
public class AdminStatsVO implements Serializable {

    /** 论坛帖子总数（不含逻辑删除） */
    private Long postTotal;

    /** 待审核帖子数（audit_status=0） */
    private Long postPending;

    /** 论坛回复总数（不含逻辑删除） */
    private Long replyTotal;

    /** 学习视频总数（不含逻辑删除） */
    private Long videoTotal;

    /** 待审核视频数（audit_status=0） */
    private Long videoPending;
}
