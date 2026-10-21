package com.studyexam.ai;

import com.studyexam.common.BusinessException;
import com.studyexam.config.AiProperties;
import com.studyexam.question.Question;
import com.studyexam.question.QuestionRepository;
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
 * AI 生成主观题解析:只处理带要点的主观题,生成后写回 analysis;编辑页预览不写库。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAnalysisServiceTest {

    @Mock AiClient aiClient;
    @Mock QuestionRepository questionRepository;

    AiAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AiAnalysisService(aiClient, new AiProperties(), questionRepository);
    }

    private Question subjective() {
        Question q = new Question();
        q.setId(5L);
        q.setSubjectId(1L);
        q.setType("SHORT_ANSWER");
        q.setStem("简述实践与认识的辩证关系");
        q.setAnswerPoints(List.of("实践是认识的基础", "认识反作用于实践"));
        return q;
    }

    @Test
    @DisplayName("生成并保存:写回 analysis,请求带上题型/要点与简洁好记的人设")
    void generateAndSaveWritesAnalysis() {
        Question q = subjective();
        when(questionRepository.findById(5L)).thenReturn(Optional.of(q));
        when(aiClient.chat(any())).thenReturn(new AiClient.AiReply(
                "考点:实践与认识\n①实践是认识的基础\n②认识反作用于实践", "qwen3.8-flash", 100, 80, 1200));

        String text = service.generateAndSave(5L);

        assertThat(text).contains("考点");
        assertThat(q.getAnalysis()).isEqualTo(text);
        verify(questionRepository).save(q);

        ArgumentCaptor<AiClient.AiRequest> captor = ArgumentCaptor.forClass(AiClient.AiRequest.class);
        verify(aiClient).chat(captor.capture());
        AiClient.AiRequest req = captor.getValue();
        assertThat(req.scene()).isEqualTo(AiAnalysisService.SCENE);
        assertThat(req.questionId()).isEqualTo(5L);
        assertThat(req.subjectId()).isEqualTo(1L);
        assertThat(req.thinking()).isFalse();
        assertThat(req.systemPrompt()).contains("简洁好记");
        assertThat(req.userText()).contains("实践是认识的基础").contains("简述实践与认识的辩证关系");
    }

    @Test
    @DisplayName("预览:只生成不写库(编辑页新建题目用)")
    void previewDoesNotWrite() {
        when(aiClient.chat(any())).thenReturn(new AiClient.AiReply("考点:…", "m", 1, 1, 10));

        String text = service.generate("ESSAY", "题干", List.of("要点"));

        assertThat(text).isEqualTo("考点:…");
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    @DisplayName("客观题拒绝生成")
    void objectiveRejected() {
        assertThatThrownBy(() -> service.generate("SINGLE_CHOICE", "题干", List.of("要点")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有主观题");
    }

    @Test
    @DisplayName("没有参考答案要点时拒绝生成")
    void emptyPointsRejected() {
        assertThatThrownBy(() -> service.generate("SHORT_ANSWER", "题干", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("参考答案要点");
    }

    @Test
    @DisplayName("模型返回的 ``` 代码块标记会被清掉")
    void codeFenceStripped() {
        when(aiClient.chat(any())).thenReturn(new AiClient.AiReply(
                "```\n考点:实践与认识\n```", "m", 1, 1, 10));

        assertThat(service.generate("SHORT_ANSWER", "题干", List.of("要点")))
                .isEqualTo("考点:实践与认识");
    }
}
