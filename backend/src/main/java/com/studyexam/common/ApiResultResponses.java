package com.studyexam.common;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 统一响应的 OpenAPI 描述。加在 Controller 方法上,避免每个方法重复写同一段 @ApiResponse。
 * 不写注解的话,springdoc 生成的是裸对象,前端拿不到可靠类型。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或业务规则拒绝(code = 1001 / 2005 / 2006 …)",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "404", description = "资源不存在(code = 2001 / 2002 / 2003 / 2004)",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "服务异常(code = 9999)",
                content = @Content(schema = @Schema(implementation = Result.class)))
})
public @interface ApiResultResponses {
}