package com.mic.culture.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.culture.model.dto.LikeCollectDTO;
import com.mic.culture.model.entity.IntangibleCulture;
import com.mic.culture.model.vo.LikeCollectStateVO;
import com.mic.culture.service.UserLikeCollectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 非遗资料点赞收藏接口：
 * - 点赞 / 收藏 / 取消（type：1点赞 2收藏）
 * - 我的点赞分页 / 我的收藏分页（仅展示仍有效的已发布资料）
 * - 点赞收藏状态查询（详情页回显当前用户按钮状态）
 * 登录态说明：网关鉴权通过后透传 X-User-Id，服务据此识别当前用户
 */
@Tag(name = "非遗资料点赞收藏接口")
@RestController
@RequestMapping("/api/culture")
@RequiredArgsConstructor
public class LikeCollectController {

    /** 类型：1点赞 */
    private static final int TYPE_LIKE = 1;

    /** 类型：2收藏 */
    private static final int TYPE_COLLECT = 2;

    private final UserLikeCollectService userLikeCollectService;

    @Operation(summary = "点赞非遗资料（登录用户，幂等）")
    @PostMapping("/like")
    public Result<Void> like(@Valid @RequestBody LikeCollectDTO dto,
                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        userLikeCollectService.likeOrCollect(userId, dto.getCultureId(), TYPE_LIKE);
        return Result.ok();
    }

    @Operation(summary = "收藏非遗资料（登录用户，幂等）")
    @PostMapping("/collect")
    public Result<Void> collect(@Valid @RequestBody LikeCollectDTO dto,
                                @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        userLikeCollectService.likeOrCollect(userId, dto.getCultureId(), TYPE_COLLECT);
        return Result.ok();
    }

    @Operation(summary = "取消点赞 / 收藏（登录用户，type：1点赞 2收藏，幂等）")
    @DeleteMapping("/likecollect")
    public Result<Void> cancel(@Valid @RequestBody LikeCollectDTO dto,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        userLikeCollectService.cancel(userId, dto.getCultureId(), dto.getType());
        return Result.ok();
    }

    @Operation(summary = "我的点赞分页（登录用户，仅展示仍有效的已发布资料）")
    @GetMapping("/like/page")
    public Result<PageResult<IntangibleCulture>> likePage(PageDto pageDto,
                                                          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(userLikeCollectService.pageMy(userId, TYPE_LIKE, pageDto));
    }

    @Operation(summary = "我的收藏分页（登录用户，仅展示仍有效的已发布资料）")
    @GetMapping("/collect/page")
    public Result<PageResult<IntangibleCulture>> collectPage(PageDto pageDto,
                                                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(userLikeCollectService.pageMy(userId, TYPE_COLLECT, pageDto));
    }

    @Operation(summary = "点赞收藏状态查询（登录用户，详情页回显）")
    @GetMapping("/likecollect/state")
    public Result<LikeCollectStateVO> state(@RequestParam(value = "cultureId") Long cultureId,
                                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(userLikeCollectService.getState(userId, cultureId));
    }

    /** 网关鉴权通过后透传 X-User-Id，缺失说明未登录 */
    private void checkLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
    }
}
