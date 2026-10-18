package com.studyexam.notify;

/**
 * 提醒通道。实现类负责把一段文字发到对应的服务。
 *
 * <p>失败时抛异常,{@code getMessage()} 会原样写进 {@code reminder_log.fail_reason} 并回给页面,
 * 所以文案必须是人能看懂的原因(如「Webhook 失效」),而不是一个堆栈。
 */
public interface NotifySender {

    /** WECOM / SERVERCHAN */
    String channel();

    /**
     * @param target 企业微信 Webhook 地址,或 Server酱 SendKey
     * @param title  标题(Server酱单独用;企业微信拼在正文前面)
     * @param body   正文
     */
    void send(String target, String title, String body) throws Exception;
}
