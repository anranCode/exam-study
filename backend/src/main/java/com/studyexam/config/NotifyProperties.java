package com.studyexam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 提醒通道的默认配置(对应 application.yml 的 {@code study-exam.notify})。
 *
 * <p>真正的通道与密钥以 {@code user_setting} 表为准(设置页可改);这里的值只是兜底:
 * 表里没填 Webhook/SendKey 时,退回环境变量({@code WECOM_WEBHOOK_URL} / {@code SERVERCHAN_KEY})。
 */
@ConfigurationProperties(prefix = "study-exam.notify")
public class NotifyProperties {

    /** 默认通道:WECOM 企业微信群机器人 / SERVERCHAN Server酱。 */
    private String channel = "WECOM";

    /** 企业微信群机器人 Webhook(兜底用)。 */
    private String wecomWebhook = "";

    /** Server酱 SendKey(兜底用)。 */
    private String serverchanKey = "";

    /** 提醒里的跳转链接前缀,如 http://localhost:8080。 */
    private String appBaseUrl = "http://localhost:8080";

    /** 发送超时(秒)。 */
    private int timeoutSeconds = 10;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getWecomWebhook() {
        return wecomWebhook;
    }

    public void setWecomWebhook(String wecomWebhook) {
        this.wecomWebhook = wecomWebhook;
    }

    public String getServerchanKey() {
        return serverchanKey;
    }

    public void setServerchanKey(String serverchanKey) {
        this.serverchanKey = serverchanKey;
    }

    public String getAppBaseUrl() {
        String url = appBaseUrl == null ? "" : appBaseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
