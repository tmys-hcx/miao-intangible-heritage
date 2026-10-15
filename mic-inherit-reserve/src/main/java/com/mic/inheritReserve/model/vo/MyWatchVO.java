package com.mic.inheritReserve.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我学习的视频展示对象：user_watch_record 关联 intangible_video 查询结果
 * （卡片展示 + 个人中心弹窗播放所需字段：videoId 唯一定位视频，content 为弹窗内视频简介）
 */
@Data
public class MyWatchVO {

    /** 学习视频ID（intangible_video.id） */
    private Long videoId;

    private String title;

    /** 视频简介（个人中心播放弹窗展示） */
    private String content;

    /** 封面缩略图OSS地址 */
    private String imgUrl;

    /** 视频OSS地址 */
    private String videoUrl;

    /** 视频时长（如 12:30） */
    private String duration;

    /** 最近完整观看时间 */
    private LocalDateTime watchTime;
}
