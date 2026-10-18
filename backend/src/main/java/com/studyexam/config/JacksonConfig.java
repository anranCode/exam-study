package com.studyexam.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间格式。
 *
 * <p>接口规范要求时间统一为 {@code yyyy-MM-dd HH:mm:ss}(见 docs/study-exam/backend/03-api-spec.md 第一节)。
 * 注意 {@code spring.jackson.date-format} 只影响 {@code java.util.Date},对 {@code LocalDateTime}
 * 完全无效——不在 Jackson 里显式注册序列化器,接口就会返回 {@code 2026-10-15T00:04:20} 这种 ISO 格式。
 *
 * <p>日期({@code LocalDate},如考试日期)保持 ISO 的 {@code yyyy-MM-dd},与规范一致,不需要改动。
 * 请求体解析同时接受带空格与带 {@code T} 两种写法,方便用 Swagger 或 curl 手工调试。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter OUTPUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(OUTPUT));
            builder.modulesToInstall(module);
        };
    }
}