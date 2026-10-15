package com.mic.culture.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.culture.model.dto.CultureCommentDTO;
import com.mic.culture.model.vo.CultureCommentVO;
import com.mic.culture.service.CultureCommentService;
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
 * 非遗资料评论接口：
 * - 评论分页（公开：顶级评论 + 二级回复，联表回填昵称头像与作者标注）
 * - 发表评论 / 回复（登录用户；parentId 为被回复评论ID，后端归一化到所属顶级评论）
 * - 删除评论（评论作者本人 或 资料发布者；顶级评论连带删除其回复）
 * 登录态说明：网关鉴权通过后透传 X-User-Id，服务据此识别当前用户
 */
@Tag(name = "非遗资料评论接口")
@RestController
@RequestMapping("/api/culture/comment")
@RequiredArgsConstructor
public class CultureCommentController {

    private final CultureCommentService cultureCommentService;

    @Operation(summary = "评论分页（公开，顶级评论 + 二级回复）")
    @GetMapping("/page")
    public Result<PageResult<CultureCommentVO>> page(PageDto pageDto,
                                                     @RequestParam(value = "cultureId") Long cultureId) {
        return Result.ok(cultureCommentService.pageByCulture(cultureId, pageDto));
    }

    @Operation(summary = "发表评论 / 回复（登录用户）")
    @PostMapping
    public Result<CultureCommentVO> publish(@Valid @RequestBody CultureCommentDTO dto,
                                            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(cultureCommentService.publish(userId, dto));
    }

    @Operation(summary = "删除评论（评论作者本人 或 资料发布者，顶级评论连带删除回复）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable("id") Long id,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        cultureCommentService.remove(userId, id);
        return Result.ok();
    }

    /** 网关鉴权通过后透传 X-User-Id，缺失说明未登录 */
    private void checkLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
    }
}
