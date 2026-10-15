package com.mic.agent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Elasticsearch Java Client（9.5.5，与本机 ES 服务端版本对齐）客户端配置，手动装配：
 *
 * 客户端三层结构（知识重点）：
 * 1、Rest5Client：低层 HTTP 通信客户端（基于 Apache HttpClient5 异步客户端），负责连接、重试、认证等；
 * 2、Rest5ClientTransport：传输层，负责把 API 请求对象编码成 HTTP 请求、把响应解码回对象（JsonpMapper 提供 JSON 编解码）；
 * 3、ElasticsearchClient：强类型 API 客户端（indices() / search() / bulk() 等），业务代码只用这一层。
 *
 * 与网上 8.x 教程的区别：8.x 时代是 RestClient（HttpClient4 时代产物）+ RestClientTransport；
 * 9.x 已移除旧实现，官方传输层改为 Rest5Client + Rest5ClientTransport（需 elasticsearch-rest5-client，
 * 已由 pom 中 elasticsearch-java 自动传递）；且新类的包名也变了——
 * 旧教程的 org.elasticsearch.client.RestClient / RestClientBuilder 在 9.x 中不存在，
 * Rest5Client / Rest5ClientBuilder 位于 co.elastic.clients.transport.rest5_client.low_level，旧教程代码直接复制会编译不过。
 *
 * 为什么手动装配而不依赖 Spring Boot 自动配置：Spring Boot 3.3.4 内置的 ES 自动配置面向 8.x 客户端
 * （条件依赖旧 RestClient 类），与本项目强制引入的 9.5.5 不匹配，条件不满足会自动退避，因此在此显式创建 Bean。
 */
@Configuration
public class EsClientConfig {

    @Value("${elasticsearch.host:127.0.0.1}")
    private String host;

    @Value("${elasticsearch.port:9200}")
    private int port;

    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    /** 账号密码：本机 ES 未开启安全认证时留空即可 */
    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    /**
     * ElasticsearchClient 是线程安全的，单例 Bean 全局共享（底层连接池由 Rest5Client 维护）；
     * 容器关闭时释放连接：Spring 对 @Bean 默认推断销毁方法会自动调用 close()（等价于 destroyMethod = "close"）
     */
    @Bean
    public ElasticsearchClient elasticsearchClient() {
        HttpHost httpHost = new HttpHost(scheme, host, port);

        Rest5ClientBuilder builder = Rest5Client.builder(httpHost);

        // ES 9.x 默认开启安全认证：配置了账号密码时附加 HTTP Basic 认证（否则匿名请求会被拒绝）
        if (StringUtils.hasText(username)) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(host, port),
                    new UsernamePasswordCredentials(username, password.toCharArray()));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        // 传输层：Jackson JSON 映射器负责 POJO 与 ES JSON 文档的互转（字段名即文档属性名）
        ElasticsearchTransport transport = new Rest5ClientTransport(builder.build(), new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
