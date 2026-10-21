package com.studyexam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 千问客户端(阿里云百炼 MaaS 的 Anthropic 兼容接口)。
 *
 * <p>按 docs/design/tech-stack.md 的要求,直接用 Spring 内置的 {@code RestClient} 发 POST,不引重型 SDK。
 * 你的 TokenPlan 走的是 Anthropic Messages 协议(/v1/messages + x-api-key),不是 OpenAI 的 chat/completions。两个 Bean:
 * <ul>
 *   <li>{@code qwenRestClient} —— 文本场景(解析生成 / 变式题 / 口诀 / 串讲),超时 45 秒;</li>
 *   <li>{@code qwenVisionRestClient} —— 视觉场景(PDF 试卷识别),超时更宽松。</li>
 * </ul>
 *
 * <p>请求形如:
 * <pre>
 * POST {base-url}/chat/completions
 * Authorization: Bearer ${DASHSCOPE_API_KEY}
 * {
 *   "model": "qwen-plus",
 *   "messages": [ {"role":"system","content":"…"}, {"role":"user","content":"…"} ],
 *   "temperature": 0.7, "max_tokens": 512, "stream": false
 * }
 * </pre>
 *
 * <p>注意:这里不在构造时校验 Key。Key 未配置时应用照常启动,由 AiClient 在调用前
 * 抛 {@code BusinessException(AI_UNAVAILABLE)},保证 AI 故障不影响练习主流程。
 */
@Configuration
public class AiConfig {

    @Bean("qwenRestClient")
    public RestClient qwenRestClient(AiProperties props) {
        return build(props, Duration.ofSeconds(props.getTimeoutSeconds()));
    }

    @Bean("qwenVisionRestClient")
    public RestClient qwenVisionRestClient(AiProperties props) {
        return build(props, Duration.ofSeconds(props.getVisionTimeoutSeconds()));
    }

    private RestClient build(AiProperties props, Duration timeout) {
        // 鉴权头(x-api-key + anthropic-version)由 AiClient 在每次请求时加上,不在这里写死。
        //
        // 底层用 JDK 的 HttpClient(JdkClientHttpRequestFactory)而不是默认的 HttpURLConnection:
        // HttpURLConnection 在这台机器上对阿里云 MaaS 的 HTTPS 端点会读到一半被重置
        // (SSL 读响应头报错),JDK HttpClient 没有这个问题。
        java.net.http.HttpClient jdkClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}