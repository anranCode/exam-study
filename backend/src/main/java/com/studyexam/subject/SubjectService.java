package com.studyexam.subject;

import com.studyexam.chapter.ChapterRepository;
import com.studyexam.chapter.dto.ChapterVO;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.question.QuestionRepository;
import com.studyexam.review.ReviewStatsProvider;
import com.studyexam.subject.dto.SubjectUpsertDTO;
import com.studyexam.subject.dto.SubjectVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final QuestionRepository questionRepository;
    private final ChapterRepository chapterRepository;
    private final ReviewStatsProvider reviewStats;

    public SubjectService(SubjectRepository subjectRepository,
                          QuestionRepository questionRepository,
                          ChapterRepository chapterRepository,
                          ReviewStatsProvider reviewStats) {
        this.subjectRepository = subjectRepository;
        this.questionRepository = questionRepository;
        this.chapterRepository = chapterRepository;
        this.reviewStats = reviewStats;
    }

    /** 科目列表。题目数量用一条聚合 SQL 查出来,不在 Java 里循环查(避免 N+1)。 */
    @Transactional(readOnly = true)
    public List<SubjectVO> list() {
        List<Subject> subjects = subjectRepository.findAllByOrderBySortOrderAscIdAsc();

        Map<Long, Long> questionCounts = new HashMap<>();
        for (SubjectRepository.SubjectQuestionCount row : subjectRepository.countQuestionsGroupedBySubject()) {
            questionCounts.put(row.getSubjectId(), row.getCnt());
        }

        return subjects.stream()
                .map(s -> toVO(s,
                        questionCounts.getOrDefault(s.getId(), 0L),
                        reviewStats.dueCount(s.getId()),
                        reviewStats.masteredCount(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubjectVO get(Long id) {
        Subject subject = findOrThrow(id);
        return toVO(subject,
                questionRepository.countBySubjectId(id),
                reviewStats.dueCount(id),
                reviewStats.masteredCount(id));
    }

    @Transactional(readOnly = true)
    public Subject findOrThrow(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.SUBJECT_NOT_FOUND, "科目不存在:id=" + id));
    }

    @Transactional
    public SubjectVO create(SubjectUpsertDTO dto) {
        if (subjectRepository.existsByCode(dto.code().trim())) {
            throw BusinessException.of(ErrorCode.SUBJECT_CODE_DUPLICATE,
                    "课程代码已存在:" + dto.code());
        }
        Subject subject = new Subject();
        apply(subject, dto);
        Subject saved = subjectRepository.save(subject);
        return toVO(saved, 0L, 0L, 0L);
    }

    @Transactional
    public SubjectVO update(Long id, SubjectUpsertDTO dto) {
        Subject subject = findOrThrow(id);
        if (subjectRepository.existsByCodeAndIdNot(dto.code().trim(), id)) {
            throw BusinessException.of(ErrorCode.SUBJECT_CODE_DUPLICATE,
                    "课程代码已存在:" + dto.code());
        }
        apply(subject, dto);
        Subject saved = subjectRepository.save(subject);
        return toVO(saved,
                questionRepository.countBySubjectId(id),
                reviewStats.dueCount(id),
                reviewStats.masteredCount(id));
    }

    /** 删除科目。有题目时拒绝——按 docs/study-exam/backend/04-features.md 阶段 1 的要求。 */
    @Transactional
    public void delete(Long id) {
        Subject subject = findOrThrow(id);
        long questionCount = questionRepository.countBySubjectId(id);
        if (questionCount > 0) {
            throw BusinessException.of(ErrorCode.SUBJECT_HAS_QUESTION,
                    "该科目下还有 " + questionCount + " 道题目,请先清空后再删除");
        }
        subjectRepository.delete(subject);
    }

    /** 科目下的章节列表(扁平,按排序)。树形结构由前端组装。 */
    @Transactional(readOnly = true)
    public List<ChapterVO> listChapters(Long subjectId) {
        findOrThrow(subjectId);

        Map<Long, Long> counts = new HashMap<>();
        for (QuestionRepository.ChapterQuestionCount row
                : questionRepository.countQuestionsGroupedByChapter(subjectId)) {
            counts.put(row.getChapterId(), row.getCnt());
        }

        return chapterRepository.findBySubjectIdOrderBySortOrderAscIdAsc(subjectId).stream()
                .map(c -> new ChapterVO(c.getId(), c.getSubjectId(), c.getParentId(),
                        c.getName(), c.getSortOrder(), counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    private void apply(Subject subject, SubjectUpsertDTO dto) {
        subject.setCode(dto.code().trim());
        subject.setName(dto.name().trim());
        subject.setShortName(dto.shortName().trim());
        if (dto.color() != null && !dto.color().isBlank()) {
            subject.setColor(dto.color().toUpperCase());
        }
        subject.setExamDate(dto.examDate());
        subject.setExamSlot(dto.examSlot() == null || dto.examSlot().isBlank() ? null : dto.examSlot());
        if (dto.totalScore() != null) {
            subject.setTotalScore(dto.totalScore());
        }
        if (dto.passScore() != null) {
            subject.setPassScore(dto.passScore());
        }
        if (dto.sortOrder() != null) {
            subject.setSortOrder(dto.sortOrder());
        }
        if (dto.enabled() != null) {
            subject.setEnabled(dto.enabled());
        }
        subject.setRemark(dto.remark());
        subject.setAiPrompt(trimToNull(dto.aiPrompt()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 到期复习数与已掌握数由 {@link ReviewStatsProvider} 提供——它读的是阶段 5 才建立的
     * {@code review_item} 表,当前返回 0。前端拿到的始终是数字而不是 null。
     */
    private SubjectVO toVO(Subject s, long questionCount, long dueReviewCount, long masteredCount) {
        return new SubjectVO(
                s.getId(), s.getCode(), s.getName(), s.getShortName(), s.getColor(),
                s.getExamDate(), s.getExamSlot(), s.getTotalScore(), s.getPassScore(),
                s.getSortOrder(), s.getEnabled(), s.getRemark(), s.getAiPrompt(),
                questionCount, dueReviewCount, masteredCount
        );
    }
}