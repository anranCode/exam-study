package com.studyexam.question;

import com.studyexam.chapter.Chapter;
import com.studyexam.chapter.ChapterRepository;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.common.PageResult;
import com.studyexam.question.dto.QuestionDetailVO;
import com.studyexam.question.dto.QuestionUpsertDTO;
import com.studyexam.question.dto.TypeCountVO;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    private static final Set<String> ALLOWED_STATUS = Set.of("DRAFT", "PUBLISHED");

    private final QuestionRepository questionRepository;
    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;

    public QuestionService(QuestionRepository questionRepository,
                           SubjectRepository subjectRepository,
                           ChapterRepository chapterRepository) {
        this.questionRepository = questionRepository;
        this.subjectRepository = subjectRepository;
        this.chapterRepository = chapterRepository;
    }

    /**
     * 各题型「可练习」的题数(只数已发布)。
     *
     * <p>「自由练习」选题型时用它:待校对的题不进抽题池,不该出现在下拉里让用户白选一场。
     */
    @Transactional(readOnly = true)
    public List<TypeCountVO> typeCounts(Long subjectId) {
        return questionRepository.countPublishedGroupedByType(subjectId).stream()
                .map(row -> {
                    String type = String.valueOf(row[0]);
                    return new TypeCountVO(type,
                            QuestionType.find(type).map(QuestionType::getLabel).orElse(type),
                            ((Number) row[1]).longValue());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<QuestionDetailVO> list(QuestionQuery query, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, clampSize(size), parseSort(sort));
        if (query.type() != null && !query.type().isBlank() && QuestionType.find(query.type()).isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "type: 未知题型 \"" + query.type() + "\"");
        }
        Page<Question> result = questionRepository.search(query, pageable);

        List<Question> rows = result.getContent();
        Map<Long, Subject> subjects = loadSubjects(rows);
        Map<Long, Chapter> chapters = loadChapters(rows);

        List<QuestionDetailVO> list = rows.stream()
                .map(q -> toVO(q, subjects.get(q.getSubjectId()), chapters.get(q.getChapterId()), true))
                .toList();
        return PageResult.of(list, result.getTotalElements(), page, pageable.getPageSize());
    }

    @Transactional(readOnly = true)
    public QuestionDetailVO get(Long id) {
        Question q = findOrThrow(id);
        return toVO(q, subjectRepository.findById(q.getSubjectId()).orElse(null),
                q.getChapterId() == null ? null : chapterRepository.findById(q.getChapterId()).orElse(null),
                true);
    }

    /** 供练习与 AI 模块复用:直接拿实体。 */
    @Transactional(readOnly = true)
    public Question findOrThrow(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.QUESTION_NOT_FOUND, "题目不存在:id=" + id));
    }

    @Transactional
    public QuestionDetailVO create(QuestionUpsertDTO dto) {
        Subject subject = subjectRepository.findById(dto.subjectId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.SUBJECT_NOT_FOUND,
                        "科目不存在:id=" + dto.subjectId()));
        Chapter chapter = resolveChapter(dto.subjectId(), dto.chapterId());

        QuestionValidator.Normalized n = QuestionValidator.validateAndNormalize(dto);

        Question q = new Question();
        apply(q, dto, n, subject.getId(), chapter == null ? null : chapter.getId());
        Question saved = questionRepository.save(q);
        return toVO(saved, subject, chapter, true);
    }

    @Transactional
    public QuestionDetailVO update(Long id, QuestionUpsertDTO dto) {
        Question q = findOrThrow(id);
        Subject subject = subjectRepository.findById(dto.subjectId())
                .orElseThrow(() -> BusinessException.of(ErrorCode.SUBJECT_NOT_FOUND,
                        "科目不存在:id=" + dto.subjectId()));
        Chapter chapter = resolveChapter(dto.subjectId(), dto.chapterId());

        QuestionValidator.Normalized n = QuestionValidator.validateAndNormalize(dto);

        apply(q, dto, n, subject.getId(), chapter == null ? null : chapter.getId());
        Question saved = questionRepository.save(q);
        return toVO(saved, subject, chapter, true);
    }

    /**
     * 删除题目。答题记录与复习队列的级联清理在阶段 4/5 落地——
     * 那两张表的建表脚本里已经声明了 {@code ON DELETE CASCADE},因此这里只需删除题目本身。
     */
    @Transactional
    public void delete(Long id) {
        Question q = findOrThrow(id);
        questionRepository.delete(q);
    }

    @Transactional(readOnly = true)
    public List<QuestionDetailVO> random(Long subjectId, Long chapterId, int count) {
        QuestionQuery query = new QuestionQuery(subjectId, chapterId, null, null, null, "PUBLISHED");
        PageResult<QuestionDetailVO> page = list(query, 1, Math.min(Math.max(count, 1), 200), null);
        return page.list();
    }

    // ── 内部 ────────────────────────────────────────────────

    private Chapter resolveChapter(Long subjectId, Long chapterId) {
        if (chapterId == null) {
            return null;
        }
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.CHAPTER_NOT_FOUND,
                        "章节不存在:id=" + chapterId));
        if (!Objects.equals(chapter.getSubjectId(), subjectId)) {
            throw BusinessException.of(ErrorCode.CHAPTER_SUBJECT_MISMATCH,
                    "章节「" + chapter.getName() + "」不属于所选科目");
        }
        return chapter;
    }

    private void apply(Question q, QuestionUpsertDTO dto, QuestionValidator.Normalized n,
                       Long subjectId, Long chapterId) {
        q.setSubjectId(subjectId);
        q.setChapterId(chapterId);
        q.setType(n.type().name());
        q.setStem(dto.stem().trim());
        q.setOptions(n.options());
        q.setAnswer(n.answer());
        q.setAnswerPoints(n.answerPoints());
        q.setAnalysis(dto.analysis());
        if (dto.difficulty() != null) {
            q.setDifficulty(dto.difficulty().byteValue());
        }
        q.setSource(dto.source());
        q.setSourceYear(dto.sourceYear());
        q.setScore(dto.score() == null ? new BigDecimal("2.0") : dto.score());
        q.setStatus(normalizeStatus(dto.status()));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "PUBLISHED";
        }
        String s = status.trim().toUpperCase();
        if (!ALLOWED_STATUS.contains(s)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "status: 只能是 DRAFT 或 PUBLISHED,当前为 \"" + status + "\"");
        }
        return s;
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 200);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    /**
     * 批量取科目,避免每条题目都查一次。
     *
     * <p>注意用 HashMap 而不是 Map.of():题目可能没有章节(chapterId 为 null),
     * 而 {@code Map.of()} 生成的不可变 Map 在 get(null) 时会抛 NPE。
     */
    private Map<Long, Subject> loadSubjects(List<Question> rows) {
        Set<Long> ids = rows.stream().map(Question::getSubjectId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Subject> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        subjectRepository.findAllById(ids).forEach(s -> map.put(s.getId(), s));
        return map;
    }

    private Map<Long, Chapter> loadChapters(List<Question> rows) {
        Set<Long> ids = rows.stream().map(Question::getChapterId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Chapter> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        chapterRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c));
        return map;
    }

    private QuestionDetailVO toVO(Question q, Subject subject, Chapter chapter, boolean includeAnswer) {
        QuestionType type = QuestionType.find(q.getType()).orElse(null);
        return new QuestionDetailVO(
                q.getId(),
                q.getSubjectId(),
                subject == null ? null : subject.getShortName(),
                subject == null ? null : subject.getColor(),
                q.getChapterId(),
                chapter == null ? null : chapter.getName(),
                q.getType(),
                type == null ? q.getType() : type.getLabel(),
                q.getStem(),
                includeAnswer ? q.getOptions() : null,
                includeAnswer ? q.getAnswer() : null,
                includeAnswer ? q.getAnswerPoints() : null,
                q.getAnalysis(),
                q.getDifficulty() == null ? null : q.getDifficulty().intValue(),
                q.getSource(),
                q.getSourceYear(),
                q.getScore(),
                q.getStatus(),
                q.getAiAnalysis(),
                q.getAiAnalysis() != null && !q.getAiAnalysis().isBlank(),
                q.getCreatedAt(),
                q.getUpdatedAt()
        );
    }
}