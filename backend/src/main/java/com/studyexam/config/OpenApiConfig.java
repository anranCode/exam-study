package com.studyexam.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 描述。前端用 openapi-typescript 从 /v3/api-docs 生成 TS 类型,
 * 因此每个 Controller 的 @Operation / DTO 的 @Schema 都不是可选项。
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "自考学习中心 API",
                version = "v1",
                description = "单用户自考备考工具后端接口。无鉴权,统一响应包装 Result<T>,code=0 表示成功。"
        )
)
public class OpenApiConfig {
}