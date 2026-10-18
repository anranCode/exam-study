package com.studyexam.importer;

import com.studyexam.common.PageResult;
import com.studyexam.importer.dto.ImportLogCreateDTO;
import com.studyexam.importer.dto.ImportLogVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 导入记录的读写。
 *
 * <p>记录由前端在每次导入结束后写入:后端只知道「这一次请求的校验结果」,
 * 看不到识别阶段的提醒,也不知道前端因为「不合格过半」而整份没提交——
 * 那些恰恰是最需要人工补录的情况,所以统一由页面汇总后提交。
 * 计数在这里做一次归一化,免得脏数据让状态判断出错。
 */
@Service
public class ImportLogService {

    private static final Logger log = LoggerFactory.getLogger(ImportLogService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ISSUES = 500;
    private static final int MAX_WARNINGS = 50;
    private static final int MAX_STEM_LENGTH = 2000;
    private static final int MAX_REASON_LENGTH = 1000;

    private final ImportLogRepository repository;

    public ImportLogService(ImportLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ImportLogVO create(ImportLogCreateDTO dto) {
        int total = nonNegative(dto.total());
        int imported = Math.min(nonNegative(dto.imported()), total);
        int duplicated = Math.min(nonNegative(dto.duplicated()), total - imported);
        int skipped = Math.min(Math.max(nonNegative(dto.skipped()), duplicated), total - imported);
        // 真正失败(不是重复)的条数
        int failed = skipped - duplicated;

        ImportLog entity = new ImportLog();
        entity.setSource(normalizeSource(dto.source()));
        entity.setSubjectCode(trimTo(dto.subjectCode(), 32, ""));
        entity.setPaperName(trimTo(dto.paperName(), 191, null));
        entity.setTotal(total);
        entity.setImported(imported);
        entity.setSkipped(skipped);
        entity.setDuplicated(duplicated);
        entity.setStatus(statusOf(total, imported, duplicated, failed));
        entity.setIssues(normalizeIssues(dto.issues()));
        entity.setWarnings(normalizeWarnings(dto.warnings()));

        ImportLog saved = repository.save(entity);
        log.info("记录导入结果 id={} source={} subject={} status={} total={} imported={} skipped={} duplicated={}",
                saved.getId(), saved.getSource(), saved.getSubjectCode(), saved.getStatus(),
                total, imported, skipped, duplicated);
        return toVO(saved);
    }

    @Transactional(readOnly = true)
    public PageResult<ImportLogVO> list(int page, int size) {
        int p = Math.max(1, page);
        int s = size < 1 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        Page<ImportLog> result = repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(p - 1, s));
        return PageResult.of(result, ImportLogService::toVO);
    }

    /** 删掉一条记录。不存在时静默成功 —— 删除本来就是幂等的。 */
    @Transactional
    public void delete(Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            log.info("删除导入记录 id={}", id);
        }
    }

    // ── 内部 ────────────────────────────────────────────────

    private static String statusOf(int total, int imported, int duplicated, int failed) {
        if (imported > 0) {
            return failed > 0 ? ImportLog.STATUS_PARTIAL : ImportLog.STATUS_IMPORTED;
        }
        if (total > 0 && duplicated >= total) {
            return ImportLog.STATUS_DUPLICATED;
        }
        return ImportLog.STATUS_FAILED;
    }

    private static String normalizeSource(String source) {
        String s = source == null ? "" : source.trim().toUpperCase();
        return switch (s) {
            case "PDF" -> "PDF";
            case "JSON" -> "JSON";
            default -> "JSON";
        };
    }

    private static List<ImportIssue> normalizeIssues(List<ImportIssue> raw) {
        List<ImportIssue> issues = new ArrayList<>();
        if (raw == null) {
            return issues;
        }
        for (ImportIssue it : raw) {
            if (it == null || it.reason() == null || it.reason().isBlank()) {
                continue;
            }
            Integer idx = it.index() != null && it.index() > 0 ? it.index() : null;
            issues.add(new ImportIssue(idx,
                    trimTo(it.stem(), MAX_STEM_LENGTH, null),
                    trimTo(it.reason(), MAX_REASON_LENGTH, "")));
            if (issues.size() >= MAX_ISSUES) {
                break;
            }
        }
        return issues;
    }

    private static List<String> normalizeWarnings(List<String> raw) {
        List<String> warnings = new ArrayList<>();
        if (raw == null) {
            return warnings;
        }
        for (String w : raw) {
            if (w == null || w.isBlank()) {
                continue;
            }
            warnings.add(trimTo(w, 500, ""));
            if (warnings.size() >= MAX_WARNINGS) {
                break;
            }
        }
        return warnings;
    }

    private static ImportLogVO toVO(ImportLog e) {
        return new ImportLogVO(
                e.getId(), e.getSource(), e.getSubjectCode(), e.getPaperName(), e.getStatus(),
                e.getTotal(), e.getImported(), e.getSkipped(), e.getDuplicated(),
                e.getIssues() == null ? List.of() : e.getIssues(),
                e.getWarnings() == null ? List.of() : e.getWarnings(),
                e.getCreatedAt());
    }

    private static int nonNegative(Integer v) {
        return v == null || v < 0 ? 0 : v;
    }

    private static String trimTo(String s, int max, String fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
