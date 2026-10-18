package com.studyexam.common;

/**
 * 业务异常。Service 层用它表达「可预期的业务失败」,由 GlobalExceptionHandler 统一转成 Result。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 用自定义文案覆盖错误码默认文案。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    // ── 常用快捷方法 ──────────────────────────────────────────
    public static BusinessException of(ErrorCode ec) {
        return new BusinessException(ec);
    }

    public static BusinessException of(ErrorCode ec, String message) {
        return new BusinessException(ec, message);
    }
}