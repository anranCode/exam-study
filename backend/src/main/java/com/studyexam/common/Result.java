package com.studyexam.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应包装。code == 0 表示成功。
 */
@Schema(description = "统一响应包装")
public record Result<T>(
        @Schema(description = "业务码,0 表示成功", example = "0") int code,
        @Schema(description = "提示信息", example = "success") String message,
        @Schema(description = "业务数据") T data
) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode ec) {
        return new Result<>(ec.getCode(), ec.getMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode ec, String message) {
        return new Result<>(ec.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}