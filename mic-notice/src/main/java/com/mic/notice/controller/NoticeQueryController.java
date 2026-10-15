package com.mic.notice.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.notice.model.vo.NoticeVO;
import com.mic.notice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息通知前端接口（经网关 /api/notice/** 转发，登录用户访问）：
 * - 我的通知列表（我收到的 + 我发出的，时间倒序）
 * - 单条标记已读 / 全部已读（仅可标记本人收到的通知）
 * 说明：与内部创建接口（/notice/create，其他微服务 Feign 调用）分离，本控制器仅面向前端；
 * 登录态说明：网关鉴权通过后透传 X-User-Id，服务据此识别当前用户
 */
@Tag(name = "消息通知前端接口")
@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class NoticeQueryController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知列表（收到 + 发出，时间倒序）")
    @GetMapping("/list")
    public Result<List<NoticeVO>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        return Result.ok(notificationService.listMine(userId));
    }

    @Operation(summary = "单条标记已读（仅本人收到的通知，幂等）")
    @PutMapping("/read/{id}")
    public Result<Void> read(@PathVariable("id") Long id,
                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        notificationService.markRead(id, userId);
        return Result.ok();
    }

    @Operation(summary = "全部已读（bizType 可选：culture/forum/order，不传则全部模块）")
    @PutMapping("/read/all")
    public Result<Void> readAll(@RequestParam(value = "bizType", required = false) String bizType,
                                @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        checkLogin(userId);
        notificationService.markAllRead(userId, bizType);
        return Result.ok();
    }

    /** 网关鉴权通过后透传 X-User-Id，缺失说明未登录 */
    private void checkLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
    }
}
