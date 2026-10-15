package com.mic.file.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务：上传文件至云端存储（阿里云OSS）
 */
public interface FileStorageService {

    /**
     * 上传文件（图片/视频）并返回公网访问地址
     *
     * @param file 上传文件
     * @return 文件公网URL
     */
    String upload(MultipartFile file);
}
