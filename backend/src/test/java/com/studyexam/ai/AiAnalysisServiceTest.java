package com.studyexam.ai;

import com.studyexam.common.BusinessException;
import com.studyexam.config.AiProperties;
import com.studyexam.question.OptionItem;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
import com.studyexam.question.dto.AiAnalysisRequest;
import com.studyexam.question.dto.AiAnalysisVO;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 生成答案/解析:科目提示词、题型板块、客观题补答案(不覆盖)、主观题保留板块。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAnalysisServiceTest {

    @Mock AiClient aiClient;
    @Mock QuestionRepository questionRepository;
    @Mock SubjectRepository subjectRepository;

    AiAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AiAnalysisService(aiClient, new AiProperties(),
                questionRepository, subjectRepository);
        Subject subject = new Subject();
        subject.setId(1L);
        subject.setName("英语(二)");
        subject.setShortName("英语");
        subject.setAiPrompt("答案一律用英文;写作题 120—150 词。");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject));
    }

    private AiAnalysisRequest request(String type, String stem) {
        return new AiAnalysisRequest(1L, type, stem, null, null, null);
    }

    private Question question(String type, String answer) {
        Question q = new Question();
        q.setId(5L);
        q.setSubjectId(1L);
        q.setType(type);
        q.setStem("题干");
        q.setAnswer(answer);
        if ("SINGLE_CHOICE".equals(type) || "MULTIPLE_CHOICE".equals(type)
                || "READING_CHOICE".equals(type)) {
            q.setOptions(List.of(
                    new OptionItem("A", "甲"),
                    new OptionItem("B", "乙"),
                    new OptionItem("C", "丙")));
        }
        return q;
    }

    private void reply(String content) {
        when(aiClient.chat(any())).thenReturn(
                new AiClient.AiReply(content, "qwen3.8-flash", 10, 20, 100));
    }

    @Test
    @DisplayName("短文写作:系统提示词带上科目专属要求,用户提示要求 120—150 词英文范文")
    void writingPromptUsesSubjectPrompt() {
        reply("【参考答案】\nOnline learning...\n【译文】\n线上学习……\n【解析】\n三段式结构。");

        service.generate(request("WRITING", "Write a composition about online learning."));

        ArgumentCaptor<AiClient.AiRequest> captor = ArgumentCaptor.forClass(AiClient.AiRequest.class);
        verify(aiClient).chat(captor.capture());
        AiClient.AiRequest req = captor.getValue();
        assertThat(req.maxTokens()).isEqualTo(1600);
        assertThat(req.systemPrompt())
                .contains("英语(二)")
                .contains("本学科专属要求")
                .contains("答案一律用英文");
        assertThat(req.userText())
                .contains("英语短文")
                .contains("120—150")
                .contains("【参考答案】")
                .contains("【译文】");
    }

    @Test
    @DisplayName("主观题:保存【参考答案】+【解析】板块,answer 为空")
    void subjectiveSectionsSaved() {
        Question q = question("SHORT_ANSWER", null);
        q.setAnswerPoints(List.of("要点一", "要点二"));
        when(questionRepository.findById(5L)).thenReturn(Optional.of(q));
        reply("【参考答案】\n①要点一 ②要点二\n【解析】\n按要点分条作答即可");

        AiAnalysisVO result = service.generateAndSave(5L);

        assertThat(result.answer()).isNull();
        assertThat(result.analysis()).contains("【参考答案】").contains("【解析】");
        assertThat(q.getAnalysis()).isEqualTo(result.analysis());
        assertThat(q.getAnswer()).isNull();
        verify(questionRepository).save(q);
    }

    @Test
    @DisplayName("客观题答案为空:把 AI 的【答案】补进题目,解析只留正文")
    void objectiveBlankAnswerFilled() {
        Question q = question("SINGLE_CHOICE", null);
        when(questionRepository.findById(5L)).thenReturn(Optional.of(q));
        reply("【答案】\nB\n【解析】\nB 正确,因为……");

        AiAnalysisVO result = service.generateAndSave(5L);

        assertThat(result.answer()).isEqualTo("B");
        assertThat(result.analysis()).isEqualTo("B 正确,因为……");
        assertThat(q.getAnswer()).isEqualTo("B");
        assertThat(q.getAnalysis()).isEqualTo("B 正确,因为……");
    }

    @Test
    @DisplayName("客观题已有答案:AI 答案被忽略,题目答案绝不覆盖")
    void objectiveExistingAnswerNeverOverwritten() {
        Question q = question("SINGLE_CHOICE", "C");
        when(questionRepository.findById(5L)).thenReturn(Optional.of(q));
        reply("【答案】\nB\n【解析】\nB 正确,因为……");

        AiAnalysisVO result = service.generateAndSave(5L);

        assertThat(result.answer()).isEqualTo("C");
        assertThat(q.getAnswer()).isEqualTo("C");
        assertThat(q.getAnalysis()).isEqualTo("B 正确,因为……");
    }

    @Test
    @DisplayName("多选答案规范化为字母升序:['d','b','a'] → ABD")
    void multipleChoiceAnswerNormalized() {
        Question q = question("MULTIPLE_CHOICE", null);
        when(questionRepository.findById(5L)).thenReturn(Optional.of(q));
        reply("【答案】\nd、b、a\n【解析】\n三个选项都对");

        assertThat(service.generateAndSave(5L).answer()).isEqualTo("ABD");
        assertThat(q.getAnswer()).isEqualTo("ABD");
    }

    @Test
    @DisplayName("选择题既没有选项也没有标准答案时拒绝生成")
    void choiceWithoutOptionsRejected() {
        AiAnalysisRequest req = new AiAnalysisRequest(1L, "SINGLE_CHOICE", "题干",
                List.of(), null, null);

        assertThatThrownBy(() -> service.generate(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("选项或标准答案");
    }

    @Test
    @DisplayName("预览只生成不写库")
    void previewDoesNotWrite() {
        reply("【参考答案】\n答案正文\n【解析】\n解析正文");

        AiAnalysisVO result = service.generate(request("SHORT_ANSWER", "题干"));

        assertThat(result.analysis()).contains("【参考答案】").contains("答案正文");
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("模型返回的 ``` 代码块标记会被清掉")
    void codeFenceStripped() {
        reply("```\n【参考答案】\n答案正文\n```");

        assertThat(service.generate(request("SHORT_ANSWER", "题干")).analysis())
                .isEqualTo("【参考答案】\n答案正文");
    }
}
