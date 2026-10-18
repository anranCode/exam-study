package com.studyexam.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 千问 TokenPlan 配置(阿里云百炼 MaaS 的 **Anthropic 兼容**入口)。
 *
 * <p>协议是 Anthropic Messages API({@code /v1/messages} + {@code x-api-key}),
 * 不是 OpenAI 的 chat/completions。密钥只留在服务端,任何接口都不返回。
 */
@ConfigurationProperties(prefix = "study-exam.ai")
public class AiProperties {

    private static final Logger log = LoggerFactory.getLogger(AiProperties.class);

    /** 密钥所在的环境变量名。 */
    private static final String KEY_ENV_NAME = "DASHSCOPE_API_KEY";

    /**
     * 兜底:进程环境里没有密钥时,再读一次 Windows 用户环境变量。
     *
     * <p>解决「用 setx 设了变量,但应用是在那之前启动的,于是怎么都读不到」这个坑——
     * 用户设完变量重启后端即可生效,不必重启整个桌面应用。
     */
    @PostConstruct
    void loadKeyFromWindowsEnvIfMissing() {
        if (apiKey != null && !apiKey.isBlank()) {
            return;
        }
        WindowsUserEnv.read(KEY_ENV_NAME).ifPresent(value -> {
            this.apiKey = value;
            log.info("进程环境里没有 {} ,已从 Windows 用户环境变量读取(长度 {})",
                    KEY_ENV_NAME, value.length());
        });
    }

    /** 是否启用 AI 辅导。关闭时 /ai/* 直接返回 6001,不发起外呼。 */
    private boolean enabled = true;

    /** 通义千问 API Key。留空时应用仍可启动,AI 接口返回 code=6001。 */
    private String apiKey = "";

    /** 百炼「应用级 · Anthropic 兼容」入口。 */
    private String baseUrl = "https://token-plan.cn-beijing.maas.aliyuncs.com/apps/anthropic";

    private String chatPath = "/v1/messages";

    /** Anthropic Messages 协议要求的版本头。 */
    private String anthropicVersion = "2023-06-01";

    /** 模型名,默认 qwen3.8-flash。 */
    private String model = "qwen3.8-flash";

    /**
     * 视觉模型名:PDF 试卷识别(扫描件)用它。用户的 TokenPlan 覆盖 qwen3.8-flash(支持视觉理解)。
     * 换模型时改环境变量 DASHSCOPE_VISION_MODEL 或本配置,不用改代码。
     */
    private String visionModel = "qwen3.8-flash";

    /** PDF 渲染 DPI:150 在清晰度与体积之间比较平衡(一页约 200—400KB JPEG)。 */
    private int pdfRenderDpi = 150;

    /** 单次识别允许的最大页数,防止误传一本书把额度烧光。 */
    private int pdfMaxPages = 30;

    /** 每次请求带几页发给视觉模型。页数多时一次带一页,识别质量更稳、失败重试代价更小。 */
    private int pdfPagesPerCall = 1;

    /** 视觉识别单次超时(秒)。整页图片大、模型思考久,比文本调用的 15 秒要宽松。 */
    private int visionTimeoutSeconds = 90;

    private int timeoutSeconds = 15;

    private double temperature = 0.7;

    /** 单次回复的最大 token 数。讲解类输出受 maxExplainChars 约束,不需要很大。 */
    private int maxTokens = 512;

    /** 讲解类输出的字数硬约束,超过视为不合格输出(见 PromptTemplates)。 */
    private int maxExplainChars = 120;

    /** 是否已配置可用的 Key。 */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatPath() {
        return chatPath;
    }

    public void setChatPath(String chatPath) {
        this.chatPath = chatPath;
    }

    public String getAnthropicVersion() {
        return anthropicVersion;
    }

    public void setAnthropicVersion(String anthropicVersion) {
        this.anthropicVersion = anthropicVersion;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(String visionModel) {
        this.visionModel = visionModel;
    }

    public int getPdfRenderDpi() {
        return pdfRenderDpi;
    }

    public void setPdfRenderDpi(int pdfRenderDpi) {
        this.pdfRenderDpi = pdfRenderDpi;
    }

    public int getPdfMaxPages() {
        return pdfMaxPages;
    }

    public void setPdfMaxPages(int pdfMaxPages) {
        this.pdfMaxPages = pdfMaxPages;
    }

    public int getPdfPagesPerCall() {
        return pdfPagesPerCall;
    }

    public void setPdfPagesPerCall(int pdfPagesPerCall) {
        this.pdfPagesPerCall = pdfPagesPerCall;
    }

    public int getVisionTimeoutSeconds() {
        return visionTimeoutSeconds;
    }

    public void setVisionTimeoutSeconds(int visionTimeoutSeconds) {
        this.visionTimeoutSeconds = visionTimeoutSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxExplainChars() {
        return maxExplainChars;
    }

    public void setMaxExplainChars(int maxExplainChars) {
        this.maxExplainChars = maxExplainChars;
    }
}