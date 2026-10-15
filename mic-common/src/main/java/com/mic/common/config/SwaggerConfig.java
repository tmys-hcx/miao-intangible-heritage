package com.mic.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j/Swagger接口文档配置
 * 访问地址：http://服务地址:端口/doc.html
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("苗韵遗珍-苗族非遗数字化传承与保护平台接口文档")
                        .description("SpringCloudAlibaba微服务接口文档（Knife4j）")
                        .version("1.0.0"));
    }
}
