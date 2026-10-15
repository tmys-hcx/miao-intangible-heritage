package com.mic.notice.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知VO（前端展示）：顶栏铃铛下拉与个人中心-消息通知面板使用
 * module / direction 为前端展示语义的映射字段，由服务端根据当前用户计算
 */
@Data
public class NoticeVO {

    /** 通知ID */
    private Long id;

    /** 模块（对应 biz_type：culture非遗文章 forum论坛帖子 order商品订单，前端模块色点 / 标签页筛选用） */
    private String module;

    /** 方向：received我收到的（接收人是当前用户）/ sent我发出的（触发人是当前用户） */
    private String direction;

    /** 是否已读（接收人视角） */
    private Boolean isRead;

    /** 通知类型：1文章评论 2文章回复 3论坛帖子回复 4论坛评论回复 5购物订单 6退款通知 7系统通知 */
    private Integer notifyType;

    /** 通知标题 */
    private String title;

    /** 通知内容摘要 */
    private String content;

    /** 业务对象ID：文章ID / 帖子ID / 订单编号（点击跳转用） */
    private String bizId;

    /** 创建时间（Jackson 全局序列化为 yyyy-MM-dd HH:mm:ss） */
    private LocalDateTime createTime;
}
