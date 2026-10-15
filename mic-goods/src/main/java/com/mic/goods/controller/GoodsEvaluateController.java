package com.mic.goods.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.goods.model.dto.EvaluateReplyDTO;
import com.mic.goods.model.dto.EvaluateSaveDTO;
import com.mic.goods.model.entity.GoodsEvaluate;
import com.mic.goods.service.GoodsEvaluateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价接口--用户端：提交评价 + 评价分页列表（免登录） + 删除本人评价 / 追问他人评价 / 删除本人追问
 * 写操作均需登录（网关鉴权后透传 X-User-Id 可信头）：仅可删除本人发布的评价与追问（不能删除别人的）；
 * 任何登录用户可追问他人评价；商品卖家可回复买家评价（同一追问接口，seller 标识由服务端计算）
 */
@Tag(name = "商品评价接口")
@RestController
@RequestMapping("/api/goods/evaluate")
@RequiredArgsConstructor
public class GoodsEvaluateController {

    private final GoodsEvaluateService goodsEvaluateService;

    /**
     * 提交商品评价（登录用户）：仅已收货订单可评价，同一订单仅可评价一次
     */
    @Operation(summary = "提交商品评价（登录用户）：仅已收货订单可评价，同一订单仅可评价一次")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody EvaluateSaveDTO dto,
                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        goodsEvaluateService.saveEvaluate(dto, userId);
        return Result.ok();
    }

    /**
     * 商品评价分页列表（免登录）：某商品全部评价记录（含评价人昵称与追问/回复列表），按创建时间倒序
     */
    @Operation(summary = "商品评价分页列表（免登录）：默认每页10条，可传goodsId查看某商品全部评价，返回含昵称与追问/回复")
    @GetMapping("/page")
    public Result<PageResult<GoodsEvaluate>> page(PageDto pageDto,
                                                  @RequestParam(value = "goodsId", required = false) Long goodsId) {
        return Result.ok(goodsEvaluateService.pageEvaluates(pageDto, goodsId));
    }

    /**
     * 删除商品评价（登录用户）：仅可删除本人发布的评价，级联删除该评价下的追问/回复
     */
    @Operation(summary = "删除商品评价（登录用户）：仅可删除本人发布的评价（不能删除别人的评价），追问/回复一并删除")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable("id") Long id,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        goodsEvaluateService.deleteEvaluate(id, userId);
        return Result.ok();
    }

    /**
     * 追问/回复商品评价（登录用户）：任何登录用户可追问他人评价，商品卖家可回复买家评价
     */
    @Operation(summary = "追问/回复商品评价（登录用户）：任何登录用户可追问他人评价，商品卖家可回复买家评价")
    @PostMapping("/reply")
    public Result<Void> reply(@Valid @RequestBody EvaluateReplyDTO dto,
                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        goodsEvaluateService.replyEvaluate(dto, userId);
        return Result.ok();
    }

    /**
     * 删除追问/回复（登录用户）：仅可删除本人发布的追问/回复
     */
    @Operation(summary = "删除追问/回复（登录用户）：仅可删除本人发布的追问/回复")
    @DeleteMapping("/reply/{id}")
    public Result<Void> removeReply(@PathVariable("id") Long id,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        goodsEvaluateService.deleteReply(id, userId);
        return Result.ok();
    }

    /**
     * 校验登录态：网关鉴权通过后透传 X-User-Id 可信头，缺失视为未登录
     */
    private void checkLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
    }
}
