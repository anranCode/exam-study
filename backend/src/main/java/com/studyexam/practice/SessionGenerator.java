package com.studyexam.practice;

import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.paper.Paper;
import com.studyexam.paper.PaperQuestion;
import com.studyexam.paper.PaperQuestionRepository;
import com.studyexam.paper.PaperRepository;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.question.QuestionType;
import com.studyexam.review.ReviewItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组题策略。五种模式的抽题规则见 docs/study-exam/backend/01-architecture.md 第三节。
 *
 * <table>
 *   <tr><td>DAILY</td><td>到期复习题(优先)+ 补足新题的差额,总数 = 用户设置的 dailyQuestionCount</td></tr>
 *   <tr><td>RANDOM</td><td>指定科目/章节范围内随机 N 题,不看复习排期</td></tr>
 *   <tr><td>CHAPTER</td><td>指定章节内随机 N 题</td></tr>
 *   <tr><td>MOCK</td><td>按指定 paperId 的题目顺序与分值完整出一套</td></tr>
 *   <tr><td>REVIEW</td><td>只抽 next_review_at &lt;= now() 的题,可按科目 + 题型筛选</td></tr>
 *   <tr><td>WRONG</td><td>从还没掌握的错题里随机抽 N 题,不看到期时间,可按科目 + 题型筛选</td></tr>
 *   <tr><td>—</td><td>不带模式,而是给了 questionIds 时:照单出题,上面五种模式都不看</td></tr>
 * </table>
 */
@Component
public class SessionGenerator {

    public static final int DEFAULT_DAILY_COUNT = 10;
    private static final int MAX_COUNT = 100;
    /** NOT IN () 的哨兵值,避免空集合导致 SQL 语法错误。 */
    private static final List<Long> NO_EXCLUSION = List.of(-1L);
    /** IN () 的哨兵值:不过滤题型时也得给 IN 一个非空列表,再靠 typeFilter 开关短路。 */
    private static final List<String> NO_TYPE_FILTER = List.of("__none__");

    private final QuestionRepository questionRepository;
    private final ReviewItemRepository reviewItemRepository;
    private final PaperRepository paperRepository;
    private final PaperQuestionRepository paperQuestionRepository;

    public SessionGenerator(QuestionRepository questionRepository,
                            ReviewItemRepository reviewItemRepository,
                            PaperRepository paperRepository,
                            PaperQuestionRepository paperQuestionRepository) {
        this.questionRepository = questionRepository;
        this.reviewItemRepository = reviewItemRepository;
        this.paperRepository = paperRepository;
        this.paperQuestionRepository = paperQuestionRepository;
    }

    /** 抽中的题;review 标记它是否来自复习队列(前端据此显示「复习」标识,study_log 也用它)。 */
    public record PickedQuestion(Question question, boolean review, String sectionName) {
    }

    public record Picked(Long paperId, List<PickedQuestion> questions) {
    }

    @Transactional(readOnly = true)
    public Picked generate(String mode, Long subjectId, Long chapterId, Integer count, Long paperId,
                           List<Long> questionIds, List<String> types) {
        String m = mode == null ? "" : mode.trim().toUpperCase();
        int n = normalizeCount(count);
        // 指定了题目就照单出题,不再看组题模式——错题本里「立即复习」某一道走的是这条
        if (questionIds != null && !questionIds.isEmpty()) {
            return explicit(questionIds, n);
        }
        List<String> typeFilter = normalizeTypes(types);
        return switch (m) {
            case "DAILY" -> daily(subjectId, chapterId, n);
            case "RANDOM" -> random(subjectId, chapterId, typeFilter, n);
            case "CHAPTER" -> chapter(subjectId, chapterId, typeFilter, n);
            case "MOCK" -> mock(paperId);
            case "REVIEW" -> review(subjectId, typeFilter, n);
            case "WRONG" -> wrong(subjectId, typeFilter, n);
            default -> throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "mode: 只能是 DAILY / RANDOM / CHAPTER / MOCK / REVIEW / WRONG,当前为 \"" + mode + "\"");
        };
    }

    /**
     * 题型筛选:去重、大写、校验合法性;空表示不限。
     *
     * <p>写了非法题型直接报错而不是悄悄忽略 —— 静默忽略会变成「用户以为在刷单选,其实全题型都抽」。
     */
    private static List<String> normalizeTypes(List<String> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        List<String> valid = new ArrayList<>();
        for (String raw : types) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String t = raw.trim().toUpperCase();
            QuestionType.find(t).orElseThrow(() -> BusinessException.of(ErrorCode.PARAM_INVALID,
                    "types: 未知题型 \"" + raw + "\""));
            if (!valid.contains(t)) {
                valid.add(t);
            }
        }
        return valid.isEmpty() ? null : valid;
    }

    /** 到期复习题优先,差额用完全没做过的新题补齐。 */
    private Picked daily(Long subjectId, Long chapterId, int count) {
        List<PickedQuestion> picked = new ArrayList<>(duePicked(subjectId, count));

        List<Long> answeredIds = picked.stream().map(p -> p.question().getId()).toList();
        if (picked.size() < count) {
            List<Question> fresh = questionRepository.pickNew(subjectId, chapterId,
                    answeredIds.isEmpty() ? NO_EXCLUSION : answeredIds, count - picked.size());
            fresh.forEach(q -> picked.add(new PickedQuestion(q, false, null)));
        }
        if (picked.isEmpty()) {
            // 没有到期题也没有新题时,退回随机抽,保证每日任务不空转
            questionRepository.pickRandom(subjectId, chapterId, false, NO_TYPE_FILTER,
                            NO_EXCLUSION, count)
                    .forEach(q -> picked.add(new PickedQuestion(q, false, null)));
        }
        ensureNotEmpty(picked, "该范围下没有可练习的题目,先去题库导入几道题吧");
        return new Picked(null, picked);
    }

    private Picked random(Long subjectId, Long chapterId, List<String> types, int count) {
        List<PickedQuestion> picked = questionRepository
                .pickRandom(subjectId, chapterId, types != null,
                        types == null ? NO_TYPE_FILTER : types, NO_EXCLUSION, count)
                .stream()
                .map(q -> new PickedQuestion(q, false, null))
                .toList();
        // 选了题型却抽不到时,大概率是该科目这些题型还是「待校对」——提示里点明,别让用户以为系统坏了
        ensureNotEmpty(picked, types == null
                ? "该范围下没有可练习的题目,先去题库导入几道题吧"
                : "该科目下没有「已发布」的这类题型。待校对的题不进练习池,先去题库把对应题型改成「已发布」");
        return new Picked(null, picked);
    }

    private Picked chapter(Long subjectId, Long chapterId, List<String> types, int count) {
        if (chapterId == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "chapterId: 章节练习必须指定章节");
        }
        return random(subjectId, chapterId, types, count);
    }

    /**
     * 指定题目出题:给哪几道就出哪几道,顺序也照给定顺序。
     *
     * <p>不走随机、不看科目章节、也不做到期过滤 —— 调用方(错题本)已经决定了要练什么。
     * 查不到的 id 直接跳过;一道都查不到时报「空场次」,与其它取不到题的分支一致。
     * 这几道题都视作来自复习队列(错题本点进来的本来就都在队列里)。
     */
    private Picked explicit(List<Long> questionIds, int limit) {
        List<Long> wanted = questionIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(limit)
                .toList();
        Map<Long, Question> byId = loadQuestions(wanted);
        List<PickedQuestion> picked = wanted.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(q -> new PickedQuestion(q, true, null))
                .toList();
        ensureNotEmpty(picked, "指定的题目不存在或已被删除,请刷新错题本");
        return new Picked(null, picked);
    }

    /** 按试卷的原始顺序与分值完整出一套。 */
    private Picked mock(Long paperId) {
        if (paperId == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "paperId: 模拟考试必须指定试卷");
        }
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PAPER_NOT_FOUND, "试卷不存在:id=" + paperId));

        List<PaperQuestion> links = paperQuestionRepository.findByPaperIdOrderBySortOrderAscIdAsc(paper.getId());
        if (links.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "试卷「" + paper.getName() + "」下还没有题目");
        }
        Map<Long, Question> byId = loadQuestions(links.stream().map(PaperQuestion::getQuestionId).toList());

        List<PickedQuestion> picked = new ArrayList<>();
        for (PaperQuestion link : links) {
            Question q = byId.get(link.getQuestionId());
            if (q != null) {
                picked.add(new PickedQuestion(q, false, link.getSectionName()));
            }
        }
        ensureNotEmpty(picked, "试卷里的题目都不可用了,请检查题库");
        return new Picked(paper.getId(), picked);
    }

    /**
     * 错题复习:只抽已到期的错题,可按科目 + 题型筛。
     *
     * <p>错题本支持「按科目、题型挑着复习」,所以题型过滤必须在这里生效 ——
     * 否则用户在下拉里选了「单项选择题」,实际发下来的却是一堆简答题。
     */
    private Picked review(Long subjectId, List<String> types, int count) {
        List<PickedQuestion> picked = duePicked(subjectId, types, count);
        ensureNotEmpty(picked, types == null
                ? "今天没有到期复习的题目,先去练几题吧"
                : "这个范围内今天没有到期的这类题型,换一种题型或直接练全部错题吧");
        return new Picked(null, picked);
    }

    /** 到期复习池(不限题型)。 */
    private List<PickedQuestion> duePicked(Long subjectId, int count) {
        return duePicked(subjectId, null, count);
    }

    /** 到期复习池:next_review_at <= now() 的题,错得多的排前面,可按题型过滤。 */
    private List<PickedQuestion> duePicked(Long subjectId, List<String> types, int count) {
        List<Long> ids = reviewItemRepository.findDueQuestionIds(1L, subjectId,
                types != null, types == null ? NO_TYPE_FILTER : types,
                LocalDateTime.now(), count);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Question> byId = loadQuestions(ids);
        // 保持到期顺序
        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(q -> new PickedQuestion(q, true, null))
                .toList();
    }

    /**
     * 错题练习:从还没掌握的错题里随机抽,不看排期。
     *
     * <p>与 {@link #review} 的差别就是「要不要等到期」。到期复习是每日任务的一部分,
     * 按排期一道道推进;错题练习是「我现在就想把错题刷一遍」,随机抽才符合预期
     * —— 否则用户点进来看到「今天没有到期复习的题目」,明明错题本里躺着几百道。
     */
    private Picked wrong(Long subjectId, List<String> types, int count) {
        List<Long> ids = reviewItemRepository.findRandomWrongQuestionIds(1L, subjectId,
                types != null, types == null ? NO_TYPE_FILTER : types, count);
        Map<Long, Question> byId = loadQuestions(ids);
        List<PickedQuestion> picked = ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(q -> new PickedQuestion(q, true, null))
                .toList();
        ensureNotEmpty(picked, types == null
                ? "错题本里还没有可练习的题,先去练几题吧"
                : "这个范围下没有这类题型的错题,换一种题型或直接练全部错题吧");
        return new Picked(null, picked);
    }

    private Map<Long, Question> loadQuestions(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return questionRepository.findAllById(new LinkedHashSet<>(ids)).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
    }

    private int normalizeCount(Integer count) {
        if (count == null || count <= 0) {
            return DEFAULT_DAILY_COUNT;
        }
        return Math.min(count, MAX_COUNT);
    }

    private void ensureNotEmpty(List<PickedQuestion> picked, String message) {
        if (picked.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, message);
        }
    }

    /** 供服务层按需保持顺序(如 MOCK)。 */
    static <T> List<T> sortedBy(List<T> list, Comparator<T> comparator) {
        return list.stream().sorted(comparator).toList();
    }
}
