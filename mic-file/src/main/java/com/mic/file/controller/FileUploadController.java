package com.mic.file.controller;

import com.mic.common.exception.BusinessException;
import com.mic.common.result.Result;
import com.mic.common.result.ResultCodeEnum;
import com.mic.file.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口：图片/视频上传至阿里云OSS，返回公网访问URL
 * 上传需登录（网关鉴权通过后透传 X-User-Id 可信头；缺失视为未登录）
 */
@Tag(name = "文件上传接口")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;

    /**
     * 上传文件（需登录）：multipart/form-data 表单，字段名 file
     */
    @Operation(summary = "上传文件（需登录）：表单字段file，支持图片/视频，返回OSS公网访问地址")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                 @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(ResultCodeEnum.NOT_LOGIN);
        }
        return Result.ok(fileStorageService.upload(file));
    }
}
