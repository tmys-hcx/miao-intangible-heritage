package com.mic.culture.model.vo;

import lombok.Data;

/**
 * 点赞收藏状态VO：详情页回显当前登录用户对资料的点赞 / 收藏状态
 */
@Data
public class LikeCollectStateVO {

    /** 当前用户是否已点赞 */
    private Boolean liked;

    /** 当前用户是否已收藏 */
    private Boolean collected;
}
