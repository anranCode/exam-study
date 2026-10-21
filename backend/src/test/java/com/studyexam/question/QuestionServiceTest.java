package com.studyexam.question;

import com.studyexam.chapter.ChapterRepository;
import com.studyexam.common.BusinessException;
import com.studyexam.subject.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 题库服务:批量入库只改 DRAFT,已入库的幂等跳过。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionServiceTest {

    @Mock QuestionRepository questionRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock ChapterRepository chapterRepository;

    QuestionService service;

    @BeforeEach
    void setUp() {
        service = new QuestionService(questionRepository, subjectRepository, chapterRepository);
    }

    private Question question(long id, String status) {
        Question q = new Question();
        q.setId(id);
        q.setStatus(status);
        return q;
    }

    @Test
    @DisplayName("批量入库:只把待校对题改成已入库,已入库的不重复处理")
    void publishMarksDraftOnly() {
        Question draft = question(1L, "DRAFT");
        Question published = question(2L, "PUBLISHED");
        when(questionRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(draft, published));

        int changed = service.publish(List.of(1L, 2L));

        assertThat(changed).isEqualTo(1);
        assertThat(draft.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        verify(questionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("批量入库:没有需要变更的题时不写库")
    void publishNoChangeDoesNotSave() {
        when(questionRepository.findAllById(List.of(2L)))
                .thenReturn(List.of(question(2L, "PUBLISHED")));

        assertThat(service.publish(List.of(2L))).isZero();
        verify(questionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("批量入库:空列表直接报错")
    void publishEmptyRejected() {
        assertThatThrownBy(() -> service.publish(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择");
    }
}
