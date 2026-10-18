package com.studyexam.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyexam.config.NotifyProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信群机器人 Webhook。
 *
 * <p>请求体 {@code {"msgtype":"text","text":{"content":"…"}}},成功时 {@code errcode == 0}。
 * 群机器人 Webhook 自带 key,泄露就等于别人能往群里发消息,所以任何日志/失败原因里都不带地址。
 */
@Component
public class WeComSender implements NotifySender {

    private final ObjectMapper objectMapper;
    private final NotifyHttp http;

    public WeComSender(ObjectMapper objectMapper, NotifyProperties props) {
        this.objectMapper = objectMapper;
        this.http = new NotifyHttp(Duration.ofSeconds(props.getTimeoutSeconds()));
    }

    @Override
    public String channel() {
        return "WECOM";
    }

    @Override
    public void send(String webhook, String title, String body) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msgtype", "text");
        payload.put("text", Map.of("content", title + "\n" + body));

        String response = http.post(webhook, "application/json",
                objectMapper.writeValueAsString(payload));
        JsonNode root = objectMapper.readTree(response);
        int errcode = root.path("errcode").asInt(0);
        if (errcode != 0) {
            throw new IllegalStateException("企业微信返回 " + errcode + ":"
                    + root.path("errmsg").asText(""));
        }
    }
}
