package com.studyexam.common;

import org.springframework.http.HttpStatus;

/**
 * 全局错误码。code == 0 表示成功。
 * 与 docs/study-exam/backend/01-architecture.md 的错误码表保持一致。
 */
public enum ErrorCode {

    SUCCESS(0, "success", HttpStatus.OK),

    PARAM_INVALID(1001, "参数校验失败", HttpStatus.BAD_REQUEST),

    SUBJECT_NOT_FOUND(2001, "科目不存在", HttpStatus.NOT_FOUND),
    CHAPTER_NOT_FOUND(2002, "章节不存在", HttpStatus.NOT_FOUND),
    QUESTION_NOT_FOUND(2003, "题目不存在", HttpStatus.NOT_FOUND),
    PAPER_NOT_FOUND(2004, "试卷不存在", HttpStatus.NOT_FOUND),

    SUBJECT_HAS_QUESTION(2005, "该科目下还有题目,请先清空后再删除", HttpStatus.BAD_REQUEST),
    CHAPTER_HAS_CONTENT(2006, "该章节下还有题目或子章节,请先清空后再删除", HttpStatus.BAD_REQUEST),
    SUBJECT_CODE_DUPLICATE(2007, "课程代码已存在", HttpStatus.BAD_REQUEST),
    CHAPTER_SUBJECT_MISMATCH(2008, "父章节不属于该科目", HttpStatus.BAD_REQUEST),

    SESSION_NOT_FOUND(3001, "练习场次不存在", HttpStatus.NOT_FOUND),
    SESSION_FINISHED(3002, "该场次已结束", HttpStatus.BAD_REQUEST),

    IMPORT_FORMAT_ERROR(4001, "导入文件格式错误", HttpStatus.BAD_REQUEST),

    NOTIFY_INVALID(5001, "推送订阅无效或已失效", HttpStatus.BAD_REQUEST),

    AI_UNAVAILABLE(6001, "AI 服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    AI_NOT_QUESTION_TYPE(6002, "该题不支持生成变式题", HttpStatus.BAD_REQUEST),

    UNKNOWN(9999, "未知异常", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}