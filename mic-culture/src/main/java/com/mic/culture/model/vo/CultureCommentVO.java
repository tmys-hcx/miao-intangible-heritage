package com.mic.culture.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 非遗资料评论VO（评论分页出参）：
 * 联表 sys_user 回填评论者昵称 / 头像 / 角色；二级回复额外回填被回复人昵称；
 * isAuthor 标记评论者是否为资料发布者（前端展示「作者」标注）；
 * userRole=1（非遗人）时昵称后展示「非遗传承人」标注
 */
@Data
public class CultureCommentVO implements Serializable {

    /** 评论ID */
    private Long id;

    /** 非遗资料ID */
    private Long cultureId;

    /** 评论者用户ID */
    private Long userId;

    /** 所属顶级评论ID（0为顶级评论；二级回复统一指向其顶级评论） */
    private Long parentId;

    /** 被回复用户ID（二级回复定向标注；顶级评论为空） */
    private Long replyUserId;

    /** 评论内容 */
    private String commentContent;

    /** 评论时间 */
    private LocalDateTime createTime;

    /** 评论者昵称（联表 sys_user 回填） */
    private String nickname;

    /** 评论者头像（联表 sys_user 回填，可能为空） */
    private String avatar;

    /** 评论者角色（联表 sys_user 回填：0管理员 1非遗人 2普通用户；非遗人前端展示「非遗传承人」标注） */
    private Integer userRole;

    /** 被回复人昵称（二级回复定向展示；顶级评论为空） */
    private String replyNickname;

    /** 是否资料作者本人的评论 / 回复（作者标注） */
    private Boolean isAuthor;

    /** 二级回复列表（仅顶级评论有值） */
    private List<CultureCommentVO> replies;
}
