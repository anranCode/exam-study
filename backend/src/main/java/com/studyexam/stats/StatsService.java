package com.studyexam.stats;

import com.studyexam.chapter.Chapter;
import com.studyexam.chapter.ChapterRepository;
import com.studyexam.question.QuestionRepository;
import com.studyexam.setting.TableAvailability;
import com.studyexam.stats.dto.ChapterStatsVO;
import com.studyexam.stats.dto.StatsOverviewVO;
import com.studyexam.stats.dto.SubjectStatsVO;
import com.studyexam.stats.dto.TrendPointVO;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统计。复杂口径一律用原生 SQL,不在 Java 里循环算。
 *
 * <p>练习相关的指标依赖 {@code answer_record} / {@code study_log} / {@code review_item},
 * 这三张表分别在阶段 4、4、5 建立。表还不存在时对应指标返回 0,接口结构保持完整。
 *
 * <p>「正确率」只算客观题({@code is_correct IS NOT NULL})——主观题不判分,
 * 进分母会让这个数字失去意义;主观题的表现看「掌握度」。
 */
@Service
public class StatsService {

    private final QuestionRepository questionRepository;
    private final TableAvailability tables;
    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;

    @PersistenceContext
    private EntityManager em;

    public StatsService(QuestionRepository questionRepository,
                        TableAvailability tables,
                        SubjectRepository subjectRepository,
                        ChapterRepository chapterRepository) {
        this.questionRepository = questionRepository;
        this.tables = tables;
        this.subjectRepository = subjectRepository;
        this.chapterRepository = chapterRepository;
    }

    @Transactional(readOnly = true)
    public StatsOverviewVO overview() {
        long totalQuestions = questionRepository.count();
        long mastered = tables.exists("review_item") ? countMastered() : 0L;

        long answered = 0L;
        long correct = 0L;
        if (tables.exists("answer_record")) {
            // 主观题不判分(is_correct 为空),因此「已作答的客观题」用 is_correct IS NOT NULL 界定
            answered = scalar("SELECT COUNT(*) FROM answer_record WHERE is_correct IS NOT NULL");
            correct = scalar("SELECT COUNT(*) FROM answer_record WHERE is_correct = 1");
        }

        LocalDate today = LocalDate.now();
        long todayAnswered = 0L;
        long todayCorrect = 0L;
        int streak = 0;
        if (tables.exists("answer_record")) {
            Object[] row = singleRow("""
                    SELECT COUNT(*) AS answered,
                           COALESCE(SUM(ar.is_correct = 1), 0) AS correct
                    FROM answer_record ar
                    WHERE DATE(ar.answered_at) = ?1
                      AND ar.is_correct IS NOT NULL
                    """, today);
            todayAnswered = ((Number) row[0]).longValue();
            todayCorrect = ((Number) row[1]).longValue();
        }
        if (tables.exists("study_log")) {
            streak = streakDays(today);
        }

        Double avgAccuracy = answered == 0 ? null : (double) correct / answered;
        return new StatsOverviewVO(totalQuestions, mastered, avgAccuracy, streak,
                todayAnswered, todayCorrect, correct, answered);
    }

    // ── 各科目 ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SubjectStatsVO> subjects() {
        List<Subject> subjects = subjectRepository.findAllByOrderBySortOrderAscIdAsc();

        Map<Long, Long> questionCounts = firstColumnAsLong(rows(
                "SELECT subject_id, COUNT(*) FROM question GROUP BY subject_id"));
        Map<Long, long[]> answers = countAndCorrect(rows("""
                SELECT q.subject_id, COUNT(*), COALESCE(SUM(ar.is_correct = 1), 0)
                FROM answer_record ar
                JOIN question q ON q.id = ar.question_id
                WHERE ar.is_correct IS NOT NULL
                GROUP BY q.subject_id
                """));
        Map<Long, Long> reviewCounts = firstColumnAsLong(rows("""
                SELECT q.subject_id, COUNT(*)
                FROM review_item ri
                JOIN question q ON q.id = ri.question_id
                WHERE ri.status = 'LEARNING'
                GROUP BY q.subject_id
                """));
        Map<Long, Long> masteredCounts = firstColumnAsLong(rows("""
                SELECT q.subject_id, COUNT(*)
                FROM review_item ri
                JOIN question q ON q.id = ri.question_id
                WHERE ri.status = 'MASTERED'
                GROUP BY q.subject_id
                """));

        return subjects.stream().map(s -> {
            long qc = questionCounts.getOrDefault(s.getId(), 0L);
            long[] ac = answers.getOrDefault(s.getId(), new long[]{0, 0});
            long mastered = masteredCounts.getOrDefault(s.getId(), 0L);
            return new SubjectStatsVO(
                    s.getId(), s.getCode(), s.getName(), s.getShortName(), s.getColor(),
                    qc, ac[0], ac[1], ratio(ac[1], ac[0]),
                    reviewCounts.getOrDefault(s.getId(), 0L), mastered, ratio(mastered, qc));
        }).toList();
    }

    // ── 近 N 天趋势 ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TrendPointVO> trend(int days) {
        int n = Math.max(1, Math.min(days, 365));
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(n - 1L);

        Map<LocalDate, long[]> byDate = new HashMap<>();
        for (Object[] r : rows("""
                SELECT DATE(answered_at) AS d, COUNT(*) AS answered,
                       COALESCE(SUM(is_correct = 1), 0) AS correct
                FROM answer_record
                WHERE is_correct IS NOT NULL AND answered_at >= ?1
                GROUP BY DATE(answered_at)
                """, start.atStartOfDay())) {
            byDate.put(toDate(r[0]), new long[]{num(r[1]), num(r[2])});
        }

        // 没作答的日期也补一个 0 点,折线才不会断
        List<TrendPointVO> points = new ArrayList<>(n);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            long[] v = byDate.getOrDefault(d, new long[]{0, 0});
            points.add(new TrendPointVO(d, v[0], v[1], ratio(v[1], v[0])));
        }
        return points;
    }

    // ── 章节掌握度 ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChapterStatsVO> chapters(Long subjectId) {
        // 科目筛选直接拼进 SQL:原生查询里绑 null 参数,驱动推不出类型会报错
        String filter = subjectId == null ? "" : " AND q.subject_id = ?1";
        Object[] params = subjectId == null ? new Object[0] : new Object[]{subjectId};

        Map<Long, Long> questionCounts = firstColumnAsLong(rows("""
                SELECT q.chapter_id, COUNT(*) FROM question q
                WHERE 1 = 1%s
                GROUP BY q.chapter_id
                """.formatted(filter), params));
        Map<Long, long[]> answers = countAndCorrect(rows("""
                SELECT q.chapter_id, COUNT(*), COALESCE(SUM(ar.is_correct = 1), 0)
                FROM answer_record ar
                JOIN question q ON q.id = ar.question_id
                WHERE ar.is_correct IS NOT NULL%s
                GROUP BY q.chapter_id
                """.formatted(filter), params));
        Map<Long, Long> masteredCounts = firstColumnAsLong(rows("""
                SELECT q.chapter_id, COUNT(*)
                FROM review_item ri
                JOIN question q ON q.id = ri.question_id
                WHERE ri.status = 'MASTERED'%s
                GROUP BY q.chapter_id
                """.formatted(filter), params));

        List<Long> chapterIds = questionCounts.keySet().stream().filter(Objects::nonNull).toList();
        Map<Long, String> names = new HashMap<>();
        if (!chapterIds.isEmpty()) {
            for (Chapter c : chapterRepository.findAllById(chapterIds)) {
                names.put(c.getId(), c.getName());
            }
        }

        List<ChapterStatsVO> list = new ArrayList<>();
        for (Map.Entry<Long, Long> e : questionCounts.entrySet()) {
            Long chapterId = e.getKey();
            long qc = e.getValue();
            long[] ac = answers.getOrDefault(chapterId, new long[]{0, 0});
            long mastered = masteredCounts.getOrDefault(chapterId, 0L);
            list.add(new ChapterStatsVO(
                    chapterId,
                    chapterId == null ? "未归章" : names.getOrDefault(chapterId, "已删除的章节"),
                    qc, ac[0], ac[1], ratio(ac[1], ac[0]), mastered, ratio(mastered, qc)));
        }
        // 掌握度低的排前面 —— 这一节就是用来「找薄弱章节」的
        list.sort(Comparator
                .comparing((ChapterStatsVO c) -> c.masteryRate() == null ? 0.0 : c.masteryRate())
                .thenComparing(c -> c.accuracy() == null ? 0.0 : c.accuracy())
                .thenComparing(c -> c.chapterId() == null ? Long.MAX_VALUE : c.chapterId()));
        return list;
    }

    // ── 内部 ────────────────────────────────────────────────

    private long countMastered() {
        return scalar("SELECT COUNT(*) FROM review_item WHERE status = 'MASTERED'");
    }

    /**
     * 连续打卡天数:从今天往前遍历 study_log,遇到缺失日期即停止。
     * 今天还没打卡不算断档——从昨天开始往前连续计算。
     */
    private int streakDays(LocalDate today) {
        int streak = 0;
        LocalDate cursor = today;
        if (!hasLogOn(today)) {
            cursor = today.minusDays(1);
        }
        while (hasLogOn(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private boolean hasLogOn(LocalDate date) {
        Object result = em.createNativeQuery(
                        "SELECT COUNT(*) FROM study_log WHERE log_date = ?1 AND question_count > 0")
                .setParameter(1, date)
                .getSingleResult();
        return ((Number) result).longValue() > 0;
    }

    private long scalar(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    private Object[] singleRow(String sql, Object param) {
        return (Object[]) em.createNativeQuery(sql).setParameter(1, param).getSingleResult();
    }

    /** 原生查询的多行结果。参数按位置绑定(?1、?2…)。 */
    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql, Object... params) {
        var query = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return (List<Object[]>) query.getResultList();
    }

    /** [key, count] 的查询结果 → key → count(键可能为 null,如「未归章」)。 */
    private Map<Long, Long> firstColumnAsLong(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put(r[0] == null ? null : num(r[0]), num(r[1]));
        }
        return map;
    }

    /** [key, count, correct] 的查询结果 → key → [count, correct]。 */
    private Map<Long, long[]> countAndCorrect(List<Object[]> rows) {
        Map<Long, long[]> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put(r[0] == null ? null : num(r[0]), new long[]{num(r[1]), num(r[2])});
        }
        return map;
    }

    private long num(Object value) {
        return ((Number) value).longValue();
    }

    private Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }

    private LocalDate toDate(Object value) {
        if (value instanceof LocalDate d) {
            return d;
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }
}
