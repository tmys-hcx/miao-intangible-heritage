package com.mic.goods.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品评价追问/回复表（两级结构）：登录用户可追问他人评价，任何用户可回复追问（双向对话）
 * 层级：parent_reply_id 为空 = 对评价的直接追问（一级）；非空 = 挂在该追问下的回复（二级）
 * 展示：一级追问按时间正序排列在评价下方；二级回复挂在各自追问下，卖家回复前端以"卖家"标签区分
 */
@Data
@TableName("goods_evaluate_reply")
public class GoodsEvaluateReply {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 评价ID（关联 goods_evaluate.id） */
    private Long evaluateId;

    /** 回复用户ID（关联 sys_user.id；商品卖家回复时即商品发布人ID） */
    private Long userId;

    /** 父级追问ID（关联本表 id）：空=对评价的直接追问；非空=该追问下的回复（仅两级，回复的回复仍挂顶层追问） */
    private Long parentReplyId;

    /** 被回复用户ID（关联 sys_user.id）：回复某条追问/回复时记录，展示"回复 xxx"；直接追问评价时为空 */
    private Long replyToUserId;

    /** 追问/回复内容 */
    private String replyContent;

    /** 回复人昵称（非表字段）：自定义 SQL 关联 sys_user 回填，展示"nickname：内容"格式 */
    @TableField(exist = false)
    private String userNickname;

    /** 被回复人昵称（非表字段）：自定义 SQL 二次关联 sys_user 回填，展示"回复 某人" */
    @TableField(exist = false)
    private String replyToNickname;

    /** 是否商品卖家回复（非表字段）：回复人 = 商品发布人时为 true，前端展示"卖家"标签 */
    @TableField(exist = false)
    private Boolean seller;

    /** 追问下的回复列表（非表字段）：仅一级追问回填，二级回复为空 */
    @TableField(exist = false)
    private List<GoodsEvaluateReply> children;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
