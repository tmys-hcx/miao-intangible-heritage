package com.mic.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 系统提示词加载器（agent-prompt.txt 全文加载）：
 * 每次向大模型发起聊天请求时，由 AssistantConfig 的 systemMessageProvider 回调本类 loadFullText()，
 * 实时读取 classpath:prompt/agent-prompt.txt 的完整文本作为 SystemMessage——
 * 不缓存、不截断、不删减，修改提示词文件后无需重启服务即可生效；
 * 读取失败或内容为空时 fail-fast 抛出异常，禁止用"残缺提示词"继续调用大模型。
 */

@Slf4j
@Component
public class AgentPromptProvider {

    /** 系统提示词资源路径（与 ChatAssistant 原有 @SystemMessage 加载位置一致） */
    private static final String PROMPT_CLASSPATH = "prompt/agent-prompt.txt";

    private final Resource promptResource = new ClassPathResource(PROMPT_CLASSPATH);

    /**
     * 读取系统提示词完整全文（每次聊天请求调用一次，保证每次都完整传入）
     */
    public String loadFullText() {
        try (InputStream inputStream = promptResource.getInputStream()) {
            String text = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException("系统提示词内容为空：" + PROMPT_CLASSPATH);
            }
            log.debug("[Agent提示词] 本次请求完整加载系统提示词，共 {} 字符", text.length());
            return text;
        } catch (IOException e) {
            log.error("[Agent提示词] 系统提示词加载失败：{}", PROMPT_CLASSPATH, e);
            throw new IllegalStateException("系统提示词加载失败：" + PROMPT_CLASSPATH, e);
        }
    }
}
