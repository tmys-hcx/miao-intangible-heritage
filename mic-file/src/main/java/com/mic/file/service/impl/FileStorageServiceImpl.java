package com.mic.file.service.impl;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.mic.common.exception.BusinessException;
import com.mic.common.result.ResultCodeEnum;
import com.mic.common.util.FileUtil;
import com.mic.file.config.OssProperties;
import com.mic.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 文件存储服务实现：文件上传至阿里云OSS
 * 存储路径约定：miao/yyyy/MM/dd/{uuid}.扩展名（uuid防止文件名冲突与路径安全问题，日期分目录便于管理）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    /** OSS对象目录前缀 */
    private static final String OBJECT_DIR = "miao/";

    /** 日期分目录格式 */
    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final OSS ossClient;

    private final OssProperties ossProperties;

    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCodeEnum.FILE_UPLOAD_ERROR, "上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (!FileUtil.isAllowed(originalFilename)) {
            throw new BusinessException(ResultCodeEnum.FILE_UPLOAD_ERROR,
                    "仅支持上传图片（jpg/jpeg/png/gif/webp/bmp）或视频（mp4）文件");
        }

        String objectName = OBJECT_DIR + DATE_DIR_FORMATTER.format(LocalDate.now())
                + "/" + FileUtil.generateFileName(originalFilename);

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        // 设置Content-Type，浏览器按类型直接预览图片/播放视频
        metadata.setContentType(MediaTypeFactory.getMediaType(originalFilename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString());

        try {
            ossClient.putObject(ossProperties.getBucketName(), objectName, file.getInputStream(), metadata);
        } catch (OSSException | ClientException | IOException e) {
            log.error("文件上传OSS失败 -> object: {}", objectName, e);
            throw new BusinessException(ResultCodeEnum.FILE_SERVICE_ERROR);
        }

        String url = buildUrl(objectName);
        log.info("文件上传成功 -> {}", url);
        return url;
    }

    /**
     * 拼接文件公网访问地址：https://{bucket}.{endpoint}/{objectName}
     * 说明：需保证Bucket读写权限为「公共读」，否则图片URL无法直接访问
     */
    private String buildUrl(String objectName) {
        return "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint() + "/" + objectName;
    }
}
