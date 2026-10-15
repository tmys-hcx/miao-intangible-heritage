package com.mic.user.controller;

import com.mic.common.model.dto.PageDto;
import com.mic.common.result.PageResult;
import com.mic.common.result.Result;
import com.mic.user.model.vo.SysNewsVO;
import com.mic.user.service.IntangibleNewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资讯公告接口
 */
@Tag(name = "资讯公告接口")
@RestController
@RequestMapping("/api/user/news")
@RequiredArgsConstructor
public class IntangibleNewsController {

    private final IntangibleNewsService intangibleNewsService;

    @Operation(summary = "资讯公告分页列表（免登录）")
    @GetMapping("/page")
    public Result<PageResult<SysNewsVO>> page(PageDto pageDto) {
        return Result.ok(intangibleNewsService.pageNews(pageDto));
    }
}
