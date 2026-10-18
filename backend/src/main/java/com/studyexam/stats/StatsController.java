package com.studyexam.stats;

import com.studyexam.common.ApiResultResponses;
import com.studyexam.common.Result;
import com.studyexam.stats.dto.ChapterStatsVO;
import com.studyexam.stats.dto.StatsOverviewVO;
import com.studyexam.stats.dto.SubjectStatsVO;
import com.studyexam.stats.dto.TrendPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "统计", description = "数据看板")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "统计总览",
            description = "总题量、已掌握数、平均正确率(只含客观题)、连续打卡天数、今日完成、累计答对。")
    @ApiResultResponses
    public Result<StatsOverviewVO> overview() {
        return Result.ok(statsService.overview());
    }

    @GetMapping("/subjects")
    @Operation(summary = "各科目正确率与掌握度",
            description = "正确率只算客观题;掌握度 = 已掌握题数 / 该科目总题量。按科目排序号返回。")
    @ApiResultResponses
    public Result<List<SubjectStatsVO>> subjects() {
        return Result.ok(statsService.subjects());
    }

    @GetMapping("/trend")
    @Operation(summary = "近 N 天答题量与正确率",
            description = "逐日返回,没作答的日期也会给 0 点,前端不用自己补。days 取值 1—365,默认 30。")
    @ApiResultResponses
    public Result<List<TrendPointVO>> trend(
            @Parameter(description = "天数,1—365") @RequestParam(defaultValue = "30") int days) {
        return Result.ok(statsService.trend(days));
    }

    @GetMapping("/chapters")
    @Operation(summary = "章节掌握度",
            description = "按章节聚合,掌握度低的排在前面 —— 用来找薄弱章节。"
                    + "subjectId 不传则统计全部科目;没归章的题目单独成为一条「未归章」。")
    @ApiResultResponses
    public Result<List<ChapterStatsVO>> chapters(
            @Parameter(description = "科目 ID,不传表示全部") @RequestParam(required = false) Long subjectId) {
        return Result.ok(statsService.chapters(subjectId));
    }
}
