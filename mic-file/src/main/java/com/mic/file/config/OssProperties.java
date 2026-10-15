package com.mic.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云OSS配置项：对应 application.yml 中 spring.aliyun.oss 前缀
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.aliyun.oss")
public class OssProperties {

    /** OSS服务地址（如 oss-cn-beijing.aliyuncs.com） */
    private String endpoint;

    /** 访问密钥ID（RAM用户） */
    private String accessKeyId;

    /** 访问密钥Secret */
    private String accessKeySecret;

    /** Bucket名称 */
    private String bucketName;
}
