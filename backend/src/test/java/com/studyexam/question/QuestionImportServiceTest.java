package com.studyexam.question;

import com.studyexam.chapter.Chapter;
import com.studyexam.chapter.ChapterRepository;
import com.studyexam.common.BusinessException;
import com.studyexam.paper.Paper;
import com.studyexam.paper.PaperQuestion;
import com.studyexam.paper.PaperQuestionRepository;
import com.studyexam.paper.PaperRepository;
import com.studyexam.question.dto.ImportItemDTO;
import com.studyexam.question.dto.ImportPaperDTO;
import com.studyexam.question.dto.ImportResult;
import com.studyexam.question.dto.QuestionImportRequest;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量导入的行为测试。规则来源 docs/study-exam/backend/04-features.md 阶段 3。
 * 用 Mockito 隔离数据库,专注验证「逐条容错 / 半数回滚 / 章节匹配 / 试卷关联」这四条。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionImportServiceTest {

    @Mock SubjectRepository subjectRepository;
    @Mock ChapterRepository chapterRepository;
    @Mock QuestionRepository questionRepository;
    @Mock PaperRepository paperRepository;
    @Mock PaperQuestionRepository paperQuestionRepository;

    QuestionImportService service;

    private static final List<OptionItem> ABCD = List.of(
            new OptionItem("A", "甲"), new OptionItem("B", "乙"),
            new OptionItem("C", "丙"), new OptionItem("D", "丁"));

    @BeforeEach
    void setUp() {
        service = new QuestionImportService(subjectRepository, chapterRepository,
                questionRepository, paperRepository, paperQuestionRepository, new ObjectMapper());

        Subject subject = new Subject();
        subject.setId(1L);
        subject.setCode("03709");
        subject.setName("马克思主义基本原理概论");
        subject.setShortName("马原");
        when(subjectRepository.findByCode("03709")).thenReturn(Optional.of(subject));

        // 模拟数据库自增主键
        long[] seq = {100};
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setId(seq[0]++);
            return q;
        });
        Paper savedPaper = new Paper();
        savedPaper.setId(9L);
        when(paperRepository.save(any(Paper.class))).thenReturn(savedPaper);

        // 默认库里没有同题干;需要造重复时在用例里重新打桩
        when(questionRepository.findIdAndStemBySubjectId(anyLong())).thenReturn(List.of());
    }

    private ImportItemDTO singleChoice(String chapterName) {
        return singleChoice(chapterName, "题干若干");
    }

    private ImportItemDTO singleChoice(String chapterName, String stem) {
        return new ImportItemDTO("一、单项选择题", "SINGLE_CHOICE", chapterName,
                stem, ABCD, "B", null, "解析", null, new BigDecimal("2.0"), 1, null);
    }

    @Test
    @DisplayName("逐条容错:第 N 条缺 answer,只跳过该条,errors 里给序号与原因")
    void skipsBadItemsAndReportsIndex() {
        List<ImportItemDTO> items = List.of(
                singleChoice("第一章", "题干一"),
                singleChoice("第一章", "题干二"),
                new ImportItemDTO("一、单项选择题", "SINGLE_CHOICE", "第一章",
                        "缺答案的题", ABCD, null, null, null, null, new BigDecimal("2.0"), 1, null));

        ImportResult r = service.importQuestions(
                new QuestionImportRequest("03709", null, items));

        assertThat(r.total()).isEqualTo(3);
        assertThat(r.imported()).isEqualTo(2);
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.errors()).hasSize(1);
        assertThat(r.errors().get(0).index()).isEqualTo(3);          // 1-based,对应用户文件的第 3 条
        assertThat(r.errors().get(0).reason()).contains("answer");
        verify(questionRepository, times(2)).save(any(Question.class));
    }

    @Test
    @DisplayName("半数回滚:失败超过一半 → 4001,一条都不入库")
    void majorityFailureRollsBack() {
        ImportItemDTO bad = new ImportItemDTO("一、单项选择题", "SINGLE_CHOICE", null,
                "缺答案", ABCD, null, null, null, null, null, 1, null);
        List<ImportItemDTO> items = List.of(singleChoice(null), bad, bad, bad);

        assertThatThrownBy(() -> service.importQuestions(
                new QuestionImportRequest("03709", null, items)))
                .isInstanceOf(BusinessException.class);

        verify(questionRepository, never()).save(any(Question.class));
        verify(paperRepository, never()).save(any(Paper.class));
    }

    @Test
    @DisplayName("章节按名匹配:命中则挂章节,未命中的留空并计入 unchaptered")
    void chapterMatchedByExactName() {
        Chapter ch = new Chapter();
        ch.setId(7L);
        ch.setSubjectId(1L);
        ch.setName("第一章 物质世界及其发展规律");
        when(chapterRepository.findBySubjectIdAndName(1L, "第一章 物质世界及其发展规律"))
                .thenReturn(List.of(ch));
        when(chapterRepository.findBySubjectIdAndName(1L, "不存在的章节"))
                .thenReturn(List.of());

        List<ImportItemDTO> items = List.of(
                singleChoice("第一章 物质世界及其发展规律", "题干一"),
                singleChoice("不存在的章节", "题干二"));

        ImportResult r = service.importQuestions(
                new QuestionImportRequest("03709", null, items));

        assertThat(r.imported()).isEqualTo(2);
        assertThat(r.unchaptered()).isEqualTo(1);

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getChapterId()).isEqualTo(7L);
        assertThat(captor.getAllValues().get(1).getChapterId()).isNull();
    }

    @Test
    @DisplayName("带 paper:建试卷,并按文件顺序建 paper_question 关联(失败条目不留洞)")
    void createsPaperWithOrderedLinks() {
        List<ImportItemDTO> items = List.of(
                new ImportItemDTO("一、单项选择题", "SINGLE_CHOICE", null, "题1", ABCD, "A", null,
                        null, null, new BigDecimal("2.0"), 1, null),
                new ImportItemDTO("一、单项选择题", "SINGLE_CHOICE", null, "缺答案", ABCD, null, null,
                        null, null, new BigDecimal("2.0"), 1, null),
                new ImportItemDTO("二、简答题", "SHORT_ANSWER", null, "题3", null, null,
                        List.of("要点一"), null, null, new BigDecimal("6.0"), 2, null));

        ImportPaperDTO paper = new ImportPaperDTO("2024年10月真题", "REAL", 2024, 100, 150, null);

        ImportResult r = service.importQuestions(
                new QuestionImportRequest("03709", paper, items));

        assertThat(r.paperId()).isEqualTo(9L);

        ArgumentCaptor<PaperQuestion> captor = ArgumentCaptor.forClass(PaperQuestion.class);
        verify(paperQuestionRepository, times(2)).save(captor.capture());
        List<PaperQuestion> links = captor.getAllValues();
        assertThat(links.get(0).getSortOrder()).isEqualTo(1);
        assertThat(links.get(0).getSectionName()).isEqualTo("一、单项选择题");
        assertThat(links.get(1).getSortOrder()).isEqualTo(2);
        assertThat(links.get(1).getSectionName()).isEqualTo("二、简答题");
    }

    @Test
    @DisplayName("多选题答案 DBA 在导入路径同样被规范化为 ABD")
    void normalizesMultipleChoiceAnswer() {
        ImportItemDTO multi = new ImportItemDTO("二、多项选择题", "MULTIPLE_CHOICE", null,
                "题干", ABCD, "DBA", null, null, null, new BigDecimal("2.0"), 2, null);

        service.importQuestions(new QuestionImportRequest("03709", null, List.of(multi)));

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(captor.capture());
        assertThat(captor.getValue().getAnswer()).isEqualTo("ABD");
    }

    @Test
    @DisplayName("subjectCode 不存在 → 报科目不存在,不是格式错误")
    void unknownSubjectRejected() {
        when(subjectRepository.findByCode("99999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.importQuestions(new QuestionImportRequest(
                "99999", null, List.of(singleChoice(null)))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("items 为空 → 4001")
    void emptyItemsRejected() {
        assertThatThrownBy(() -> service.importQuestions(
                new QuestionImportRequest("03709", null, List.of())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("文件解析:非法 JSON → 4001,原因可读")
    void invalidJsonFileRejected() {
        assertThatThrownBy(() -> service.parseJson(
                new java.io.ByteArrayInputStream("{ not json ".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    @DisplayName("题目来源取试卷名,年份缺省取试卷年份")
    void sourceAndYearDefaultFromPaper() {
        ImportPaperDTO paper = new ImportPaperDTO("2024年10月真题", "REAL", 2024, 100, 150, null);
        service.importQuestions(new QuestionImportRequest("03709", paper,
                List.of(singleChoice(null))));

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("2024年10月真题");
        assertThat(captor.getValue().getSourceYear()).isEqualTo(2024);
    }

    // ── 题干去重 ────────────────────────────────────────────

    /** 让「库里已有」这道题:归一化题干 → questionId=77 */
    private void stubExisting(String stem) {
        when(questionRepository.findIdAndStemBySubjectId(1L))
                .thenReturn(List.<Object[]>of(new Object[]{77L, stem}));
    }

    @Test
    @DisplayName("重复:与库里已有题干相同 → 跳过并记入 duplicates,不报校验失败")
    void duplicateAgainstDbSkipped() {
        stubExisting("题干若干");

        ImportResult r = service.importQuestions(
                new QuestionImportRequest("03709", null, List.of(singleChoice(null))));

        assertThat(r.imported()).isZero();
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.errors()).isEmpty();                    // 不是「不合格」
        assertThat(r.duplicates()).hasSize(1);
        assertThat(r.duplicates().get(0).index()).isEqualTo(1);
        assertThat(r.duplicates().get(0).reason()).contains("id=77");
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("重复:同一批里题干相同 → 只入第一条,第二条指向本批第 1 条")
    void duplicateWithinBatchSkipped() {
        ImportResult r = service.importQuestions(new QuestionImportRequest("03709", null,
                List.of(singleChoice(null, "同一道题"), singleChoice(null, "同一道题"))));

        assertThat(r.imported()).isEqualTo(1);
        assertThat(r.duplicates()).hasSize(1);
        assertThat(r.duplicates().get(0).index()).isEqualTo(2);
        assertThat(r.duplicates().get(0).reason()).contains("本批第 1 条");
        verify(questionRepository, times(1)).save(any(Question.class));
    }

    @Test
    @DisplayName("重复:只差空格与标点也算同一道题")
    void duplicateIgnoresWhitespaceAndPunctuation() {
        stubExisting("下列关于民族主义的说法不正确的是");

        ImportResult r = service.importQuestions(new QuestionImportRequest("03709", null,
                List.of(singleChoice(null, "下列关于“民族主义”的说法,不正确的是。"))));

        assertThat(r.imported()).isZero();
        assertThat(r.duplicates()).hasSize(1);
    }

    @Test
    @DisplayName("重复不算失败:一半是重复时不触发整份回滚")
    void duplicatesDoNotTriggerMajorityRollback() {
        stubExisting("重复题");

        ImportResult r = service.importQuestions(new QuestionImportRequest("03709", null,
                List.of(singleChoice(null, "重复题"), singleChoice(null, "新题"))));

        assertThat(r.imported()).isEqualTo(1);
        assertThat(r.duplicates()).hasSize(1);
    }

    @Test
    @DisplayName("全是重复时不建空试卷")
    void allDuplicatesCreateNoPaper() {
        stubExisting("重复题");
        ImportPaperDTO paper = new ImportPaperDTO("2024年10月真题", "REAL", 2024, 100, 150, null);

        ImportResult r = service.importQuestions(new QuestionImportRequest("03709", paper,
                List.of(singleChoice(null, "重复题"))));

        assertThat(r.imported()).isZero();
        assertThat(r.paperId()).isNull();
        verify(paperRepository, never()).save(any(Paper.class));
    }
}