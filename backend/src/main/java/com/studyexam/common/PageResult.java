package com.studyexam.common;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应。字段名与 docs/study-exam/backend/03-api-spec.md 一致:list / total / page / size / pages。
 */
@Schema(description = "分页响应")
public record PageResult<T>(
        @Schema(description = "当前页数据") List<T> list,
        @Schema(description = "总条数", example = "128") long total,
        @Schema(description = "页码,从 1 开始", example = "1") int page,
        @Schema(description = "每页条数", example = "20") int size,
        @Schema(description = "总页数", example = "7") int pages
) {

    /** 页码对外从 1 开始,Spring Data 内部从 0 开始,这里统一转换。 */
    public static <T> PageResult<T> of(Page<T> springPage) {
        return new PageResult<>(
                springPage.getContent(),
                springPage.getTotalElements(),
                springPage.getNumber() + 1,
                springPage.getSize(),
                springPage.getTotalPages()
        );
    }

    public static <E, T> PageResult<T> of(Page<E> springPage, Function<E, T> mapper) {
        return of(springPage.map(mapper));
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        int pages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResult<>(list, total, page, size, pages);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0L, page, size, 0);
    }
}