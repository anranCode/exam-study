package com.studyexam.practice.dto;

import com.studyexam.question.OptionItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 练习中的题目。安全约定:绝不包含 answer / answerPoints / analysis,
 * 答案只在提交作答后由 AnswerResultVO 返回。
 */
@Schema(description = "练习中的题目(不含答案)")
public record PracticeQuestionVO(
        @Schema(description = "题目 ID") Long id,
        @Schema(description = "科目 ID") Long subjectId,
        @Schema(description = "科目简称") String subjectShortName,
        @Schema(description = "科目颜色") String subjectColor,
        @Schema(description = "章节 ID") Long chapterId,
        @Schema(description = "章节名") String chapterName,
        @Schema(description = "题型") String type,
        @Schema(description = "题型中文名") String typeLabel,
        @Schema(description = "题干") String stem,
        @Schema(description = "选项(客观题)") List<OptionItem> options,
        @Schema(description = "本题分值") BigDecimal score,
        @Schema(description = "是否来自到期复习队列") boolean isReview,
        @Schema(description = "试卷原始大题名,仅 MOCK 模式有值") String sectionName
) {
}
