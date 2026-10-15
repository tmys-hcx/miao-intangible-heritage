package com.mic.culture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.culture.model.entity.IntangibleCulture;
import com.mic.culture.model.entity.UserLikeCollect;
import com.mic.culture.model.vo.LikeCollectStateVO;

/**
 * 点赞收藏服务接口（登录用户操作，仅针对已发布资料）
 */
public interface UserLikeCollectService extends IService<UserLikeCollect> {

    /** 点赞 / 收藏（type：1点赞 2收藏）：已操作时幂等忽略 */
    void likeOrCollect(Long userId, Long cultureId, Integer type);

    /** 取消点赞 / 收藏（type：1点赞 2收藏）：未操作时幂等忽略 */
    void cancel(Long userId, Long cultureId, Integer type);

    /** 我的点赞 / 收藏分页（type：1点赞 2收藏）：仅展示仍有效的已发布资料 */
    PageResult<IntangibleCulture> pageMy(Long userId, Integer type, PageDto pageDto);

    /** 查询当前用户对指定资料的点赞 / 收藏状态（详情页回显） */
    LikeCollectStateVO getState(Long userId, Long cultureId);
}
