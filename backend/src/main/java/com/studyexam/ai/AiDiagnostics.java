package com.studyexam.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyexam.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 连通性自检。
 *
 * <p>用 JDK 自带的 {@link HttpClient} 直接打**配置里的入口**,返回上游原始状态码与响应体;
 * 另发一次**不带密钥的对照请求**——两者一比,「密钥没发出去」还是「密钥无效」一目了然。
 * 这里只打配置指定的官方入口,不接受任意 URL,避免变成开放的探测工具。
 */
@Service
public class AiDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(AiDiagnostics.class);

    private final AiProperties props;
    private final ObjectMapper objectMapper;

    public AiDiagnostics(AiProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> diagnose() {
        Map<String, Object> result = new LinkedHashMap<>();
        String key = props.getApiKey();
        result.put("keyPresent", key != null && !key.isBlank());
        result.put("keyLength", key == null ? 0 : key.length());
        result.put("keyHint", hint(key));
        result.put("baseUrl", props.getBaseUrl());
        result.put("chatPath", props.getChatPath());
        result.put("configuredModel", props.getModel());

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", props.getModel(),
                    "max_tokens", 16,
                    "messages", List.of(Map.of("role", "user", "content", "hi"))));
        } catch (Exception e) {
            body = "{\"model\":\"qwen-plus\",\"max_tokens\":16,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}";
        }

        String url = props.getBaseUrl() + props.getChatPath();
        List<Map<String, Object>> attempts = new ArrayList<>();
        attempts.add(attempt(url, body, true));
        attempts.add(attempt(url, body, false));
        result.put("attempts", attempts);
        return result;
    }

    private Map<String, Object> attempt(String url, String body, boolean withKey) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("url", url);
        entry.put("withKey", withKey);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .header("anthropic-version", props.getAnthropicVersion())
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (withKey && props.getApiKey() != null) {
                builder.header("x-api-key", props.getApiKey());
            }
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            entry.put("status", response.statusCode());
            entry.put("body", snippet(response.body()));
        } catch (Exception e) {
            entry.put("status", "ERROR");
            entry.put("body", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        log.info("AI 自检 withKey={} {} → {}", withKey, url, entry.get("status"));
        return entry;
    }

    private String hint(String key) {
        if (key == null || key.isBlank()) {
            return "(未配置)";
        }
        if (key.length() <= 12) {
            return key.substring(0, 3) + "…";
        }
        return key.substring(0, 6) + "…" + key.substring(key.length() - 4)
                + "(共 " + key.length() + " 字符)";
    }

    private String snippet(String body) {
        if (body == null || body.isBlank()) {
            return "(空响应体)";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 400 ? flat.substring(0, 400) + "…" : flat;
    }
}