package com.studyexam.review;

import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.PageResult;
import com.studyexam.common.Result;
import com.studyexam.review.dto.DueSummaryVO;
import com.studyexam.review.dto.ReviewItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review")
@Tag(name = "复习", description = "错题本与复习排期")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/due-summary")
    @Operation(summary = "到期复习汇总",
            description = "今日页首屏数据:到期总数、按科目拆分、今日新题配额、是否已打卡。")
    @ApiResultResponses
    public Result<DueSummaryVO> dueSummary() {
        return Result.ok(reviewService.dueSummary());
    }

    @GetMapping("/items")
    @Operation(summary = "错题本列表",
            description = "复习队列,按下次复习时间升序(最该复习的排前面)。"
                    + "可按科目、队列状态、是否到期筛选。"
                    + "响应**不含答案与解析** —— 要看答案是点进去重做,不是在这里看。")
    @ApiResultResponses
    public Result<PageResult<ReviewItemVO>> listItems(
            @Parameter(description = "科目 ID,不传表示全部") @RequestParam(required = false) Long subjectId,
            @Parameter(description = "队列状态:LEARNING 在队列 / MASTERED 已掌握;不传表示不限")
            @RequestParam(required = false) String status,
            @Parameter(description = "只看已到期的") @RequestParam(required = false) Boolean due,
            @Parameter(description = "页码,从 1 开始") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页条数,最大 200") @RequestParam(required = false) Integer size) {
        return Result.ok(reviewService.listItems(subjectId, status, due, page, size));
    }

    @PutMapping("/items/{questionId}/mastered")
    @Operation(summary = "手动标记已掌握",
            description = "把这道题移出复习队列(status 置为 MASTERED)。重复调用不报错。")
    @ApiResultResponses
    public Result<Void> markMastered(
            @Parameter(description = "题目 ID") @PathVariable Long questionId) {
        reviewService.markMastered(questionId);
        return Result.ok();
    }

    @DeleteMapping("/items/{questionId}")
    @Operation(summary = "移出错题本",
            description = "删掉这道题的排期记录,以后不会再出现。题目本身不受影响。")
    @ApiResultResponses
    public Result<Void> remove(
            @Parameter(description = "题目 ID") @PathVariable Long questionId) {
        reviewService.remove(questionId);
        return Result.ok();
    }
}
