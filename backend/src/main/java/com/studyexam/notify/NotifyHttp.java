package com.studyexam.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 发送提醒用的小 HTTP 封装。
 *
 * <p>用 JDK 的 HttpClient 而不是默认的 HttpURLConnection —— 与 AI 那边保持一致,
 * 这台机器上后者对某些 HTTPS 端点会读一半被重置。
 */
final class NotifyHttp {

    private final HttpClient client;
    private final Duration timeout;

    NotifyHttp(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    String post(String url, String contentType, String body) throws Exception {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            // 不把原串带进异常:里面可能是带 SendKey 的地址
            throw new IllegalStateException("地址格式不对,请检查是否粘贴完整");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("返回 HTTP " + response.statusCode()
                    + ":" + abbreviate(response.body()));
        }
        return response.body();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > 200 ? flat.substring(0, 200) + "…" : flat;
    }
}
