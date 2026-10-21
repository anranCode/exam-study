package com.studyexam.practice;

import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.paper.Paper;
import com.studyexam.paper.PaperQuestion;
import com.studyexam.paper.PaperQuestionRepository;
import com.studyexam.paper.PaperRepository;
import com.studyexam.practice.dto.MockTemplateVO;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.question.QuestionType;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模拟考试的组卷模板。
 *
 * <p>模板不是写死的:从该科目最近一份有内容的真题试卷里读出「题型 → 题数、每题分值」,
 * 再查题库里这些题型已发布了多少道,前端据此决定哪些题型能用、最多能出几道。
 * 这样以后导入新的真题,组卷结构会跟着变,不需要改代码。
 */
@Service
public class MockExamService {

    private static final Logger log = LoggerFactory.getLogger(MockExamService.class);

    private final SubjectRepository subjectRepository;
    private final PaperRepository paperRepository;
    private final PaperQuestionRepository paperQuestionRepository;
    private final QuestionRepository questionRepository;

    public MockExamService(SubjectRepository subjectRepository,
                           PaperRepository paperRepository,
                           PaperQuestionRepository paperQuestionRepository,
                           QuestionRepository questionRepository) {
        this.subjectRepository = subjectRepository;
        this.paperRepository = paperRepository;
        this.paperQuestionRepository = paperQuestionRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public MockTemplateVO template(Long subjectId) {
        if (subjectId == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "subjectId: 请先选择科目");
        }
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.SUBJECT_NOT_FOUND,
                        "科目不存在:id=" + subjectId));

        Long paperId = paperRepository.findLatestPaperIdWithQuestions(subjectId).orElse(null);
        if (paperId == null) {
            // 没有真题可参考时返回空模板,由页面提示「先去导入一份真题」
            return new MockTemplateVO(subjectId, subject.getShortName(), subject.getName(),
                    null, null, null, null, null, List.of());
        }
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.PAPER_NOT_FOUND,
                        "试卷不存在:id=" + paperId));

        List<PaperQuestion> links = paperQuestionRepository
                .findByPaperIdOrderBySortOrderAscIdAsc(paperId);
        Map<Long, Question> questions = questionRepository.findAllById(
                        links.stream().map(PaperQuestion::getQuestionId).toList())
                .stream().collect(Collectors.toMap(Question::getId, q -> q));

        // 按试卷顺序收集题型;同题型合并成一段(一份卷子里同一题型通常连续出现)
        Map<String, SectionBuilder> sections = new LinkedHashMap<>();
        for (PaperQuestion link : links) {
            Question q = questions.get(link.getQuestionId());
            if (q == null) {
                continue;
            }
            sections.computeIfAbsent(q.getType(), type -> new SectionBuilder(type, typeLabel(type)))
                    .add(q.getScore());
        }

        Map<String, Integer> available = publishedCounts(subjectId);
        List<MockTemplateVO.Section> result = sections.values().stream()
                .map(s -> new MockTemplateVO.Section(s.type, s.label, s.count, s.score,
                        available.getOrDefault(s.type, 0)))
                .toList();

        log.info("模拟考试模板 subject={} paper={} 题型{}段", subject.getCode(), paperId, result.size());
        return new MockTemplateVO(subjectId, subject.getShortName(), subject.getName(),
                paper.getId(), paper.getName(), paper.getYear(),
                paper.getTotalScore() == null ? 100 : paper.getTotalScore(),
                paper.getDurationMin() == null ? 150 : paper.getDurationMin(),
                result);
    }

    private Map<String, Integer> publishedCounts(Long subjectId) {
        Map<String, Integer> counts = new HashMap<>();
        for (Object[] row : questionRepository.countPublishedGroupedByType(subjectId)) {
            if (row[0] != null) {
                counts.put(String.valueOf(row[0]), ((Number) row[1]).intValue());
            }
        }
        return counts;
    }

    private static String typeLabel(String type) {
        return QuestionType.find(type).map(QuestionType::getLabel).orElse(type);
    }

    /** 一个题型的累计:题数 + 每题分值(取试卷内该题型的最小分值)。 */
    private static final class SectionBuilder {
        private final String type;
        private final String label;
        private int count;
        private BigDecimal score;

        private SectionBuilder(String type, String label) {
            this.type = type;
            this.label = label;
        }

        private void add(BigDecimal questionScore) {
            count++;
            if (questionScore == null) {
                return;
            }
            if (score == null || questionScore.compareTo(score) < 0) {
                score = questionScore;
            }
        }
    }
}
