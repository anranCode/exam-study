package com.studyexam.importer;

import com.studyexam.common.PageResult;
import com.studyexam.importer.dto.ImportLogCreateDTO;
import com.studyexam.importer.dto.ImportLogVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导入记录的状态判定与计数归一化。
 *
 * <p>页面提交的是它自己数出来的数字,这里再做一次收敛:
 * 计数越界、状态标签写错都不能污染「还差哪几道题」这份清单。
 */
@ExtendWith(MockitoExtension.class)
class ImportLogServiceTest {

    @Mock ImportLogRepository repository;

    ImportLogService service;

    @BeforeEach
    void setUp() {
        service = new ImportLogService(repository);
    }

    private void stubSave() {
        when(repository.save(any(ImportLog.class))).thenAnswer(inv -> {
            ImportLog e = inv.getArgument(0);
            e.setId(1L);
            e.setCreatedAt(LocalDateTime.of(2026, 10, 18, 18, 0));
            return e;
        });
    }

    private ImportLogCreateDTO dto(int total, int imported, int skipped, int duplicated,
                                   List<ImportIssue> issues, List<String> warnings) {
        return new ImportLogCreateDTO("pdf", "13000", "2024年10月真题",
                total, imported, skipped, duplicated, issues, warnings);
    }

    @Test
    @DisplayName("部分入库 → PARTIAL,题干与原因原样保留")
    void createPartial() {
        stubSave();
        ImportLogVO vo = service.create(dto(10, 8, 2, 0,
                List.of(new ImportIssue(3, "题干 A", "客观题 options 不能为空")),
                List.of("第 2 页识别失败,已重试成功")));

        assertThat(vo.status()).isEqualTo(ImportLog.STATUS_PARTIAL);
        assertThat(vo.source()).isEqualTo("PDF");
        assertThat(vo.issues()).hasSize(1);
        assertThat(vo.issues().get(0).stem()).isEqualTo("题干 A");
        assertThat(vo.warnings()).containsExactly("第 2 页识别失败,已重试成功");
    }

    @Test
    @DisplayName("一条没入库且全是重复 → DUPLICATED,不算失败")
    void createAllDuplicated() {
        stubSave();
        ImportLogVO vo = service.create(dto(10, 0, 10, 10, List.of(), List.of()));
        assertThat(vo.status()).isEqualTo(ImportLog.STATUS_DUPLICATED);
    }

    @Test
    @DisplayName("计数越界会被收敛,状态按收敛后的数字判定")
    void createClampsCounts() {
        stubSave();
        // 声称入库 20 条,但总数只有 10
        ImportLogVO vo = service.create(dto(10, 20, 20, 0, List.of(), List.of()));
        assertThat(vo.imported()).isEqualTo(10);
        assertThat(vo.skipped()).isZero();
        assertThat(vo.status()).isEqualTo(ImportLog.STATUS_IMPORTED);
    }

    @Test
    @DisplayName("没有原因的问题条目不进清单")
    void createDropsBlankReason() {
        stubSave();
        ImportLogVO vo = service.create(dto(2, 0, 2, 0,
                List.of(new ImportIssue(1, "题干", "  "), new ImportIssue(null, null, "整份未入库")),
                List.of()));
        assertThat(vo.issues()).hasSize(1);
        assertThat(vo.issues().get(0).index()).isNull();
    }

    @Test
    @DisplayName("删除不存在的记录不报错,也不调用 deleteById")
    void deleteMissingIsNoop() {
        when(repository.existsById(9L)).thenReturn(false);
        service.delete(9L);
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("每页最多 100 条,页码最小为 1")
    void listClampsPaging() {
        when(repository.findAllByOrderByCreatedAtDescIdDesc(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        PageResult<ImportLogVO> result = service.list(0, 999);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.page()).isEqualTo(1);
    }
}
