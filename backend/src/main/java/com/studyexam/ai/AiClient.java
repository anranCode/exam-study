package com.studyexam.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 千问调用客户端。
 *
 * <p>你的 TokenPlan 走阿里云百炼「应用级 · Anthropic 兼容」地址,协议是
 * <b>Anthropic Messages API</b>:
 * <pre>
 * POST {base-url}/v1/messages
 * x-api-key: <key>
 * anthropic-version: 2023-06-01
 * { "model": "qwen3.8-flash", "max_tokens": N,
 *   "system": "人设…",
 *   "messages": [ {"role":"user","content":"文本"} ] }           // 纯文本
 *              或 [ {"role":"user","content":[ {"type":"text","text":"…"},
 *                                                {"type":"image","source":{"type":"base64",
 *                                                  "media_type":"image/jpeg","data":"…"}} ] } ]
 * </pre>
 * 响应是 {@code content[]}(可能先有一个 thinking 块,再是 text 块),usage 里是
 * {@code input_tokens / output_tokens}。
 *
 * <p>职责只有三件:拼请求体、发 POST、把响应与用量翻译成本项目的类型。
 * 失败一律映射为 {@code code = 6001} 并带上上游可读原因;每次真实调用写 {@code ai_call_log};
 * 密钥只存服务端,任何接口都不返回。
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final AiProperties props;
    private final RestClient textClient;
    private final RestClient visionClient;
    private final AiCallLogRepository callLogRepository;
    private final ObjectMapper objectMapper;

    public AiClient(AiProperties props,
                    @Qualifier("qwenRestClient") RestClient textClient,
                    @Qualifier("qwenVisionRestClient") RestClient visionClient,
                    AiCallLogRepository callLogRepository,
                    ObjectMapper objectMapper) {
        this.props = props;
        this.textClient = textClient;
        this.visionClient = visionClient;
        this.callLogRepository = callLogRepository;
        this.objectMapper = objectMapper;
    }

    /** 一张要交给视觉模型的图片。 */
    public record ImagePart(String mimeType, byte[] data) {
    }

    public record AiRequest(String scene, String model, String systemPrompt, String userText,
                            List<ImagePart> images, int maxTokens, double temperature,
                            boolean thinking, Long subjectId, Long questionId) {

        public static AiRequest text(String scene, String model, String system, String user,
                                     int maxTokens, double temperature) {
            return new AiRequest(scene, model, system, user, List.of(), maxTokens, temperature, true, null, null);
        }

        public static AiRequest vision(String scene, String model, String system, String user,
                                       List<ImagePart> images, int maxTokens) {
            return new AiRequest(scene, model, system, user, images, maxTokens, 0.2, true, null, null);
        }

        /** 结构化提取用:关掉思考,速度和输出体量都降一个数量级。 */
        public AiRequest withoutThinking() {
            return new AiRequest(scene, model, systemPrompt, userText, images, maxTokens,
                    temperature, false, subjectId, questionId);
        }
    }

    public record AiReply(String content, String model, int tokensIn, int tokensOut, long durationMs) {
    }

    public AiReply chat(AiRequest request) {
        if (!props.isConfigured()) {
            throw BusinessException.of(ErrorCode.AI_UNAVAILABLE,
                    "还没配置千问 API Key:请把 DASHSCOPE_API_KEY 写进环境变量后重启后端");
        }
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = buildBody(request);
            RestClient client = request.images() == null || request.images().isEmpty()
                    ? textClient : visionClient;
            // 按字节读而不是 String:上游个别响应会以 application/octet-stream 返回,
            // 交给 String 转换器会直接抛「无法提取响应」,盖住真正的状态码/错误
            byte[] rawBytes = client.post()
                    .uri(props.getChatPath())
                    .header("x-api-key", props.getApiKey())
                    .header("anthropic-version", props.getAnthropicVersion())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            String raw = rawBytes == null ? "" : new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);

            JsonNode root = objectMapper.readTree(raw.isEmpty() ? "{}" : raw);
            String content = extractText(root.path("content"));
            int tokensIn = root.path("usage").path("input_tokens").asInt(0);
            int tokensOut = root.path("usage").path("output_tokens").asInt(0);
            String model = root.path("model").asText(request.model());
            long cost = System.currentTimeMillis() - start;

            if (content.isBlank()) {
                throw new IllegalStateException("模型返回了空内容");
            }
            writeLog(request, model, tokensIn, tokensOut, cost, true, null);
            log.info("AI 调用成功 scene={} model={} in={} out={} 耗时={}ms",
                    request.scene(), model, tokensIn, tokensOut, cost);
            return new AiReply(content, model, tokensIn, tokensOut, cost);

        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            String rawBody = e.getResponseBodyAsString();
            String reason = readableUpstreamError(rawBody, e.getStatusCode().value());
            long cost = System.currentTimeMillis() - start;
            writeLog(request, request.model(), 0, 0, cost, false, reason);
            log.warn("AI 调用被拒 scene={} status={} reason={} 原文={}",
                    request.scene(), e.getStatusCode(), reason,
                    rawBody == null ? "" : rawBody.substring(0, Math.min(rawBody.length(), 500)));
            throw BusinessException.of(ErrorCode.AI_UNAVAILABLE, reason);
        } catch (ResourceAccessException e) {
            long cost = System.currentTimeMillis() - start;
            String reason = "调用千问超时或网络不通:" + e.getMessage();
            writeLog(request, request.model(), 0, 0, cost, false, reason);
            log.warn("AI 调用超时 scene={} {}", request.scene(), e.getMessage());
            throw BusinessException.of(ErrorCode.AI_UNAVAILABLE, reason);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            String reason = "调用千问失败:" + e.getMessage();
            writeLog(request, request.model(), 0, 0, cost, false, reason);
            log.warn("AI 调用失败 scene={}", request.scene(), e);
            throw BusinessException.of(ErrorCode.AI_UNAVAILABLE, reason);
        }
    }

    /** Anthropic 响应的 content[] 里可能混着 thinking 块,只取 text 块拼起来。 */
    private String extractText(JsonNode content) {
        if (content == null) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText(""))) {
                sb.append(block.path("text").asText(""));
            }
        }
        return sb.toString();
    }

    private Map<String, Object> buildBody(AiRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.model());
        body.put("max_tokens", request.maxTokens());
        body.put("temperature", request.temperature());
        if (!request.thinking()) {
            body.put("thinking", Map.of("type", "disabled"));
        }
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            body.put("system", request.systemPrompt());
        }
        body.put("messages", List.of(Map.of("role", "user", "content", buildUserContent(request))));
        return body;
    }

    /** 无图片时是纯字符串;有图片时是 Anthropic 的内容块数组(text + image)。 */
    private Object buildUserContent(AiRequest request) {
        if (request.images() == null || request.images().isEmpty()) {
            return request.userText();
        }
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (request.userText() != null && !request.userText().isBlank()) {
            blocks.add(Map.of("type", "text", "text", request.userText()));
        }
        for (ImagePart image : request.images()) {
            blocks.add(Map.of("type", "image", "source", Map.of(
                    "type", "base64",
                    "media_type", image.mimeType(),
                    "data", Base64.getEncoder().encodeToString(image.data()))));
        }
        return blocks;
    }

    /** Anthropic 错误形如 {"type":"error","error":{"type":"…","message":"…"}}。 */
    private String readableUpstreamError(String body, int status) {
        if (body == null || body.isBlank()) {
            return "千问返回 " + status + ",响应体为空";
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String message = node.at("/error/message").asText("");
            String code = node.at("/error/type").asText("");
            if (message.isBlank()) {
                message = node.path("message").asText("");
            }
            if (!message.isBlank()) {
                return "千问返回 " + status + ":" + message + (code.isBlank() ? "" : "(" + code + ")");
            }
        } catch (Exception ignored) {
            // 非 JSON,走下面的兜底
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return "千问返回 " + status + ":" + (flat.length() > 300 ? flat.substring(0, 300) + "…" : flat);
    }

    private void writeLog(AiRequest request, String model, int tokensIn, int tokensOut,
                          long durationMs, boolean success, String failReason) {
        try {
            AiCallLog entry = new AiCallLog();
            entry.setScene(request.scene());
            entry.setModel(model);
            entry.setTokensIn(tokensIn);
            entry.setTokensOut(tokensOut);
            entry.setDurationMs((int) durationMs);
            entry.setSuccess(success);
            entry.setFailReason(failReason == null ? null
                    : failReason.substring(0, Math.min(failReason.length(), 255)));
            entry.setSubjectId(request.subjectId());
            entry.setQuestionId(request.questionId());
            callLogRepository.save(entry);
        } catch (Exception e) {
            log.debug("写 ai_call_log 失败:{}", e.getMessage());
        }
    }

    /** 给自检接口用:当前生效的配置(不含密钥本身)。 */
    public Map<String, Object> describeConfig() {
        Map<String, Object> info = new HashMap<>();
        info.put("configured", props.isConfigured());
        info.put("keyPresent", props.getApiKey() != null && !props.getApiKey().isBlank());
        info.put("keyLength", props.getApiKey() == null ? 0 : props.getApiKey().length());
        info.put("baseUrl", props.getBaseUrl());
        info.put("chatPath", props.getChatPath());
        info.put("model", props.getModel());
        info.put("visionModel", props.getVisionModel());
        info.put("visionTimeoutSeconds", props.getVisionTimeoutSeconds());
        return info;
    }
}