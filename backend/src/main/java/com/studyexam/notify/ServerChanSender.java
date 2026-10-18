package com.studyexam.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyexam.config.NotifyProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Server酱(方糖)推送。
 *
 * <p>请求 {@code POST https://sctapi.ftqq.com/<SendKey>.send},表单字段 {@code title} / {@code desp},
 * 成功时 {@code code == 0}。SendKey 同样只留在服务端。
 */
@Component
public class ServerChanSender implements NotifySender {

    private static final String ENDPOINT = "https://sctapi.ftqq.com/%s.send";

    private final ObjectMapper objectMapper;
    private final NotifyHttp http;

    public ServerChanSender(ObjectMapper objectMapper, NotifyProperties props) {
        this.objectMapper = objectMapper;
        this.http = new NotifyHttp(Duration.ofSeconds(props.getTimeoutSeconds()));
    }

    @Override
    public String channel() {
        return "SERVERCHAN";
    }

    @Override
    public void send(String sendKey, String title, String body) throws Exception {
        String url = ENDPOINT.formatted(URLEncoder.encode(sendKey.trim(), StandardCharsets.UTF_8));
        String form = "title=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                + "&desp=" + URLEncoder.encode(body, StandardCharsets.UTF_8);

        String response = http.post(url, "application/x-www-form-urlencoded;charset=UTF-8", form);
        JsonNode root = objectMapper.readTree(response);
        int code = root.path("code").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("Server酱返回 " + code + ":"
                    + root.path("message").asText(""));
        }
    }
}
