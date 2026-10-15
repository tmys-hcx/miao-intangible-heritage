package com.mic.common.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用通知创建DTO（跨服务 Feign 调用通知服务使用）：
 * 由业务服务在事件发生时组装标题 / 内容（评论者昵称、文章标题等业务数据在业务服务侧联表可得），
 * 通知服务只负责落库，与业务服务数据保持解耦，评论 / 论坛 / 购物通知共用
 */
@Data
public class NoticeCreateDTO implements Serializable {

    /** 接收用户ID（关联sys_user.id） */
    @NotNull(message = "接收用户ID不能为空")
    private Long receiveUserId;

    /** 触发用户ID（关联sys_user.id），系统通知为空 */
    private Long sendUserId;

    /** 通知类型：1文章评论 2文章回复 3论坛帖子回复 4论坛评论回复 5购物订单 6退款通知 7系统通知 */
    @NotNull(message = "通知类型不能为空")
    private Integer notifyType;

    /** 通知标题（最长128字符，与 notification.title 定义一致） */
    @NotBlank(message = "通知标题不能为空")
    @Size(max = 128, message = "通知标题不能超过128字符")
    private String title;

    /** 通知内容摘要（最长512字符，与 notification.content 定义一致） */
    @Size(max = 512, message = "通知内容不能超过512字符")
    private String content;

    /** 业务类型：culture非遗文章 forum论坛帖子 order商品订单 */
    @Size(max = 32, message = "业务类型不能超过32字符")
    private String bizType;

    /** 业务对象ID：文章ID / 帖子ID / 订单编号（点击通知跳转对应详情用） */
    @Size(max = 64, message = "业务对象ID不能超过64字符")
    private String bizId;
}
