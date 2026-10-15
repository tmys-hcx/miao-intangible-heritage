package com.mic.file.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云OSS客户端配置：创建全局唯一的OSS客户端Bean，应用关闭时自动shutdown释放资源
 */
@Configuration
@RequiredArgsConstructor
public class OssConfig {

    private final OssProperties ossProperties;

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        // 显式使用V4签名：新创建的Bucket仅支持V4签名，旧Bucket同样兼容V4
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        return OSSClientBuilder.create()
                .credentialsProvider(new DefaultCredentialProvider(
                        ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret()))
                .clientConfiguration(configuration)
                .region(resolveRegion(ossProperties.getEndpoint()))
                .endpoint(ossProperties.getEndpoint())
                .build();
    }

    /**
     * 从endpoint解析地域（V4签名必需）：oss-cn-beijing.aliyuncs.com → cn-beijing；内网域名自动去掉-internal
     */
    private static String resolveRegion(String endpoint) {
        String region = endpoint == null ? "" : endpoint.trim();
        if (region.startsWith("oss-")) {
            region = region.substring(4);
        }
        int dot = region.indexOf('.');
        if (dot > 0) {
            region = region.substring(0, dot);
        }
        return region.replace("-internal", "");
    }
}
