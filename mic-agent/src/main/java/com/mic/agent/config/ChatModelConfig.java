package com.mic.agent.config;

import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 对话模型配置（显式装配，完全确定性）：
 * 按 application.yml 的 langchain4j.open-ai.* 配置显式创建 @AiService 引用的两个模型 Bean——
 * deepseekChatModel / deepseekStreamingChatModel（Bean 名与 @AiService 引用名一致）。
 *
 * 为什么不用 starter 自动装配（dev.langchain4j.openai.spring.AutoConfig）：
 * 版本线已从 1.19.0-beta29（starter 面向 Boot 3.5.x，与项目 3.3.4 不兼容）调整为 1.10.0-beta18（面向 3.3.8，兼容，见根 pom）；
 * 该 AutoConfig 在本环境曾先后出现“openAiChatModel Bean 未产出（Cannot resolve reference）”与
 * “自定义同名 Bean 冲突（BeanDefinitionOverrideException，其模型 Bean 无 @ConditionalOnMissingBean 退让）”两类启动失败。
 * 为确保完全确定性与可控性，继续在 application.yml 通过 spring.autoconfigure.exclude 关闭该自动装配，改由本类显式装配。
 *
 * HTTP 客户端说明：显式使用与 starter 同款的 SpringRestClient（langchain4j-http-client-spring-restclient，
 * 显式声明于 pom），避免 classpath 存在多个 HTTP 客户端实现时 SPI 默认选择的不确定性；
 * 并锁定 JDK HttpClient 走 HTTP/1.1（JdkClientHttpRequestFactory + HttpClient.Version.HTTP_1_1）：
 * 排查发往 DeepSeek 的对话请求返回“HTTP 200 但响应体仅 `{` 即 EOF”（JsonEOFException）问题时，
 * 同一客户端调用 DashScope 正常、PowerShell 直连 DeepSeek 也正常，怀疑与 DeepSeek 网关
 * （openresty / 腾讯 EdgeOne CDN）在 JDK HttpClient 默认 HTTP/2 协商下的兼容性有关，锁定 1.1 以规避；
 * 如需回退原行为，将 http11RestClientBuilder() 替换回 SpringRestClient.builder() 即可。
 */
@Configuration
public class ChatModelConfig {

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.log-requests:false}")
    private boolean logRequests;

    @Value("${langchain4j.open-ai.chat-model.log-responses:false}")
    private boolean logResponses;

    @Value("${langchain4j.open-ai.streaming-chat-model.log-requests:false}")
    private boolean streamingLogRequests;

    @Value("${langchain4j.open-ai.streaming-chat-model.log-responses:false}")
    private boolean streamingLogResponses;

    /** 同步对话模型：@AiService 的 chatModel 引用（与 streaming-chat-model 共用 chat-model 的地址/密钥/模型名参数） */
    @Bean
    public OpenAiChatModel deepseekChatModel() {
        return OpenAiChatModel.builder()
                .httpClientBuilder(http11RestClientBuilder())
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    /** 流式对话模型：@AiService 的 streamingChatModel 引用（Flux/SSE 逐 token 输出） */
    @Bean
    public OpenAiStreamingChatModel deepseekStreamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .httpClientBuilder(http11RestClientBuilder())
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .logRequests(streamingLogRequests)
                .logResponses(streamingLogResponses)
                .build();
    }

    /**
     * 构造锁定 HTTP/1.1 的 SpringRestClient 构建器（SpringRestClientBuilder，两个对话模型共用）：
     * JDK HttpClient 默认与服务器协商 HTTP/2，而 DeepSeek 网关（openresty + 腾讯 EdgeOne CDN）
     * 在默认版本协商下曾返回“HTTP 200 + body 仅 `{` 即 EOF”的截断响应（JsonEOFException）；
     * 该问题在 PowerShell 直连、同客户端调 DashScope 时均未出现，锁定 HTTP/1.1 以规避组合兼容性问题。
     */
    private SpringRestClientBuilder http11RestClientBuilder() {
        return SpringRestClient.builder()
                .restClientBuilder(RestClient.builder()
                        .requestFactory(new JdkClientHttpRequestFactory(
                                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build())));
    }
}
