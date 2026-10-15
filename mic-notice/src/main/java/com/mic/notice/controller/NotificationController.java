package com.mic.notice.controller;

import com.mic.common.model.dto.NoticeCreateDTO;
import com.mic.common.result.Result;
import com.mic.notice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息通知接口：
 * - 创建通知（内部接口：非遗文化 / 论坛 / 购物等业务服务在事件发生时经 Feign 调用）
 * 说明：内部接口不经过网关（服务间直连调用），无鉴权要求；
 * 前端查询 / 标记已读等接口后续按模块补充
 */
@Tag(name = "消息通知接口")
@RestController
@RequestMapping("/notice")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "创建通知（内部接口，其他微服务 Feign 调用）")
    @PostMapping("/create")
    public Result<Void> create(@Valid @RequestBody NoticeCreateDTO dto) {
        notificationService.create(dto);
        return Result.ok();
    }
}
