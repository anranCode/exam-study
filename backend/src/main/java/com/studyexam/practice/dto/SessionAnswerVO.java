package com.studyexam.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 回看某次练习时的逐题作答。 */
@Schema(description = "逐题作答")
public record SessionAnswerVO(
        @Schema(description = "题目 ID") Long questionId,
        @Schema(description = "题干") String stem,
        @Schema(description = "题型") String type,
        @Schema(description = "题型中文名") String typeLabel,
        @Schema(description = "科目简称") String subjectShortName,
        @Schema(description = "章节名") String chapterName,
        @Schema(description = "我的作答") String userAnswer,
        @Schema(description = "是否正确;主观题为空") Boolean isCorrect,
        @Schema(description = "信心度 SURE / UNSURE / WRONG") String confidence,
        @Schema(description = "主观题自评得分") Integer selfScore,
        @Schema(description = "主观题要点总数") Integer totalPoints,
        @Schema(description = "正确答案") String correctAnswer,
        @Schema(description = "参考答案要点") List<String> answerPoints,
        @Schema(description = "解析") String analysis,
        @Schema(description = "作答时间") LocalDateTime answeredAt
) {
}
