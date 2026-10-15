package com.mic.notice.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 消息通知表实体：评论 / 回复 / 购物等业务事件产生的通知记录
 * notify_type 通知类型：1文章评论 2文章回复 3论坛帖子回复 4论坛评论回复 5购物订单 6退款通知 7系统通知
 * biz_type 业务类型：culture非遗文章 forum论坛帖子 order商品订单（biz_id 为对应文章ID / 帖子ID / 订单编号，点击跳转用）
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收用户ID（关联sys_user.id） */
    private Long receiveUserId;

    /** 触发用户ID（关联sys_user.id），系统通知为空 */
    private Long sendUserId;

    /** 通知类型：1文章评论 2文章回复 3论坛帖子回复 4论坛评论回复 5购物订单 6退款通知 7系统通知 */
    private Integer notifyType;

    /** 通知标题 */
    private String title;

    /** 通知内容摘要 */
    private String content;

    /** 业务类型：culture非遗文章 forum论坛帖子 order商品订单 */
    private String bizType;

    /** 业务对象ID：文章ID / 帖子ID / 订单编号 */
    private String bizId;

    /** 是否已读，0未读 1已读 */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
