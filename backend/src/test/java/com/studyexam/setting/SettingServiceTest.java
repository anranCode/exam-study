package com.studyexam.setting;

import com.studyexam.common.BusinessException;
import com.studyexam.setting.dto.PracticeDefaultsUpsertDTO;
import com.studyexam.setting.dto.SettingUpsertDTO;
import com.studyexam.setting.dto.SettingVO;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 设置服务:自由练习默认条件的落库、清空与校验。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingServiceTest {

    @Mock UserSettingRepository repository;
    @Mock SubjectRepository subjectRepository;

    SettingService service;

    @BeforeEach
    void setUp() {
        service = new SettingService(repository, subjectRepository);
        UserSetting s = new UserSetting();
        s.setUserId(1L);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(s));
        when(repository.save(any(UserSetting.class))).thenAnswer(i -> i.getArgument(0));
        when(subjectRepository.existsById(anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("自由练习默认条件:题源、科目、题型、题量落库并按 VO 返回")
    void practiceDefaultsSaved() {
        SettingVO vo = service.update(withDefaults(new PracticeDefaultsUpsertDTO(
                "WRONG",
                2L,
                List.of("single_choice", "SHORT_ANSWER", "single_choice"),
                25,
                3L,
                List.of("ESSAY"),
                7)));

        assertThat(vo.practiceDefaults().source()).isEqualTo("WRONG");
        assertThat(vo.practiceDefaults().librarySubjectId()).isEqualTo(2L);
        assertThat(vo.practiceDefaults().libraryTypes())
                .containsExactly("SINGLE_CHOICE", "SHORT_ANSWER");
        assertThat(vo.practiceDefaults().libraryCount()).isEqualTo(25);
        assertThat(vo.practiceDefaults().wrongSubjectId()).isEqualTo(3L);
        assertThat(vo.practiceDefaults().wrongTypes()).containsExactly("ESSAY");
        assertThat(vo.practiceDefaults().wrongCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("自由练习默认条件:科目传 null、题型传空表示「全部」")
    void practiceDefaultsCanBeCleared() {
        SettingVO vo = service.update(withDefaults(new PracticeDefaultsUpsertDTO(
                "LIBRARY", null, List.of(), 10, null, null, 10)));

        assertThat(vo.practiceDefaults().librarySubjectId()).isNull();
        assertThat(vo.practiceDefaults().libraryTypes()).isEmpty();
        assertThat(vo.practiceDefaults().wrongSubjectId()).isNull();
        assertThat(vo.practiceDefaults().wrongTypes()).isEmpty();
    }

    @Test
    @DisplayName("自由练习默认条件:非法题源 / 题量 / 题型 / 科目都报错")
    void practiceDefaultsValidated() {
        assertThatThrownBy(() -> service.update(withDefaults(
                new PracticeDefaultsUpsertDTO("OTHER", null, null, null, null, null, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("source");

        assertThatThrownBy(() -> service.update(withDefaults(
                new PracticeDefaultsUpsertDTO("LIBRARY", null, null, 0, null, null, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("libraryCount");

        assertThatThrownBy(() -> service.update(withDefaults(
                new PracticeDefaultsUpsertDTO("LIBRARY", null, List.of("NOT_A_TYPE"), null, null, null, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知题型");

        when(subjectRepository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.update(withDefaults(
                new PracticeDefaultsUpsertDTO("LIBRARY", 99L, null, null, null, null, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("科目不存在");
    }

    private SettingUpsertDTO withDefaults(PracticeDefaultsUpsertDTO defaults) {
        return new SettingUpsertDTO(null, null, null, null, null, null, null, null, null, defaults);
    }
}
