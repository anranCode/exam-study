package com.studyexam.question;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyexam.chapter.Chapter;
import com.studyexam.chapter.ChapterRepository;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.paper.Paper;
import com.studyexam.paper.PaperQuestion;
import com.studyexam.paper.PaperQuestionRepository;
import com.studyexam.paper.PaperRepository;
import com.studyexam.question.dto.ImportItemDTO;
import com.studyexam.question.dto.ImportPaperDTO;
import com.studyexam.question.dto.ImportResult;
import com.studyexam.question.dto.QuestionImportRequest;
import com.studyexam.question.dto.QuestionUpsertDTO;
import com.studyexam.subject.Subject;
import com.studyexam.subject.SubjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 题库批量导入。★ 这是「试卷录入」的落地入口,也是后续 PDF 识别管道(千问转 JSON)的目标接口。
 *
 * <p>策略(定稿于 docs/study-exam/backend/04-features.md 阶段 3):
 * <ul>
 *   <li><b>逐条容错</b>:单条校验失败跳过并记 {@code {序号, 原因}},其余照常入库——
 *       手工整理的真题难免有零星笔误,不能因为第 12 条错就让 39 条白干;</li>
 *   <li><b>半数回滚</b>:失败条目超过总数一半,判定「文件格式根本不对」,整体回滚并返回 4001;</li>
 *   <li><b>章节按名匹配</b>:找不到就留空并计数提示,不自动建章节(章节结构应由人决定);</li>
 *   <li>带 {@code paper} 字段时同时建试卷与 {@code paper_question} 关联,
 *       {@code sort_order} 按文件顺序,回看试卷能还原原始结构。</li>
 * </ul>
 *
 * <p>校验复用 {@link QuestionValidator},保证导入路径与单题录入路径的规则完全一致
 * (包括多选题答案 DBA→ABD 这类规范化)。
 */
@Service
public class QuestionImportService {

    private static final Logger log = LoggerFactory.getLogger(QuestionImportService.class);
    private static final int MAX_STEM_LENGTH = 2000;

    /** 题干归一化时抹掉的字符:空白(含全角空格)与中英标点。只去符号,不动任何实词。 */
    private static final Pattern STEM_NOISE = Pattern.compile(
            "[\\s\\u3000，。、；：？！,.;:?!\"'“”‘’（）()【】\\[\\]《》〈〉<>—–\\-_·…~`$￥%]+");

    private final SubjectRepository subjectRepository;
    private final ChapterRepository chapterRepository;
    private final QuestionRepository questionRepository;
    private final PaperRepository paperRepository;
    private final PaperQuestionRepository paperQuestionRepository;
    private final ObjectMapper objectMapper;

    public QuestionImportService(SubjectRepository subjectRepository,
                                 ChapterRepository chapterRepository,
                                 QuestionRepository questionRepository,
                                 PaperRepository paperRepository,
                                 PaperQuestionRepository paperQuestionRepository,
                                 ObjectMapper objectMapper) {
        this.subjectRepository = subjectRepository;
        this.chapterRepository = chapterRepository;
        this.questionRepository = questionRepository;
        this.paperRepository = paperRepository;
        this.paperQuestionRepository = paperQuestionRepository;
        this.objectMapper = objectMapper;
    }

    /** 从上传的 JSON 文件流导入。解析失败属于文件格式错误,直接 4001。 */
    public QuestionImportRequest parseJson(java.io.InputStream in) {
        try {
            return objectMapper.readValue(in, QuestionImportRequest.class);
        } catch (JsonProcessingException e) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR,
                    "文件不是合法的 JSON:" + firstLine(e.getOriginalMessage()));
        } catch (java.io.IOException e) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR, "文件读取失败:" + e.getMessage());
        }
    }

    @Transactional
    public ImportResult importQuestions(QuestionImportRequest req) {
        if (req == null || req.items() == null || req.items().isEmpty()) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR, "items 缺失或为空");
        }
        if (isBlank(req.subjectCode())) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR, "subjectCode 缺失");
        }
        Subject subject = subjectRepository.findByCode(req.subjectCode().trim())
                .orElseThrow(() -> BusinessException.of(ErrorCode.SUBJECT_NOT_FOUND,
                        "科目不存在,请先在科目管理里建课:" + req.subjectCode()));

        List<ImportItemDTO> items = req.items();
        List<ImportResult.ImportError> errors = new ArrayList<>();
        List<ImportResult.ImportError> duplicates = new ArrayList<>();
        // 与 items 平行:null 表示该条没入库(校验失败或与已有题目重复)
        Question[] valid = new Question[items.size()];
        int unchaptered = 0;
        // 题目来源优先用试卷名(如「2024年10月真题」),没有试卷时用科目代码
        String defaultSource = req.paper() != null && !isBlank(req.paper().name())
                ? trimTo(req.paper().name(), 64) : trimTo(subject.getCode(), 64);
        Integer defaultYear = req.paper() == null ? null : req.paper().year();

        // 题干去重索引:先把库里该科目的题干按归一化形式放进去;本批新入库的随后也放进去,
        // 这样「与库里重复」和「同一批里自己重复」用同一套判断。
        Map<String, String> seenStems = loadStemIndex(subject.getId());

        for (int i = 0; i < items.size(); i++) {
            ImportItemDTO item = items.get(i);
            try {
                String stemKey = normalizeStem(item == null ? null : item.stem());
                String prior = stemKey.isEmpty() ? null : seenStems.get(stemKey);
                if (prior != null) {
                    duplicates.add(new ImportResult.ImportError(i + 1,
                            "题干重复,已跳过(与" + prior + "重复)"));
                    continue;
                }
                Question entity = toEntity(subject, item, defaultSource, defaultYear);
                if (!stemKey.isEmpty()) {
                    seenStems.put(stemKey, "本批第 " + (i + 1) + " 条");
                }
                valid[i] = entity;
                if (entity.getChapterId() == null && !isBlank(item.chapterName())) {
                    unchaptered++;   // 写了章节名但没匹配上
                }
            } catch (BusinessException e) {
                errors.add(new ImportResult.ImportError(i + 1, e.getMessage()));
            } catch (Exception e) {
                errors.add(new ImportResult.ImportError(i + 1, "解析失败:" + e.getMessage()));
            }
        }

        // 半数回滚:失败超过一半,几乎一定是文件整体格式不对,不该把垃圾写进库。
        // 重复**不计入**这个判断 —— 把同一份试卷再导一遍是正常操作,不该因此整份回滚。
        if (errors.size() * 2 > items.size()) {
            throw BusinessException.of(ErrorCode.IMPORT_FORMAT_ERROR,
                    "共 " + items.size() + " 条,失败 " + errors.size() + " 条,超过一半,整体取消未入库。"
                            + "第一条原因:" + errors.get(0).reason());
        }

        int imported = 0;
        List<Question> savedQuestions = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (valid[i] != null) {
                Question saved = questionRepository.save(valid[i]);
                savedQuestions.add(saved);
                imported++;
            } else {
                savedQuestions.add(null);   // 保持与 items 的平行下标,建试卷关联时要用
            }
        }

        Long paperId = null;
        // 一条都没入库时不建空试卷:整份重复导一遍会走到这里
        if (imported > 0 && req.paper() != null && !isBlank(req.paper().name())) {
            paperId = createPaper(subject.getId(), req.paper(), items, savedQuestions);
        }

        log.info("导入完成 subject={} total={} imported={} skipped={} duplicate={} paperId={}",
                subject.getCode(), items.size(), imported,
                errors.size() + duplicates.size(), duplicates.size(), paperId);
        return new ImportResult(items.size(), imported, errors.size() + duplicates.size(),
                unchaptered, paperId, errors, duplicates);
    }

    // ── 内部 ────────────────────────────────────────────────

    /**
     * 题干归一化:去掉空白(含全角空格)与中英标点,英文转小写。
     *
     * <p>为什么不一字不差地比:同一道题在两次识别里差一个空格或一个句号就不该算两道。
     * 反过来也不能太宽——只去标点空白,不动任何实词。
     */
    private static String normalizeStem(String stem) {
        if (stem == null) {
            return "";
        }
        return STEM_NOISE.matcher(stem).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** 该科目已有题目的「归一化题干 → 来源描述」索引。 */
    private Map<String, String> loadStemIndex(Long subjectId) {
        Map<String, String> index = new HashMap<>();
        for (Object[] row : questionRepository.findIdAndStemBySubjectId(subjectId)) {
            String key = normalizeStem((String) row[1]);
            if (!key.isEmpty()) {
                index.putIfAbsent(key, "已有题目 id=" + row[0]);
            }
        }
        return index;
    }

    private Question toEntity(Subject subject, ImportItemDTO item, String defaultSource, Integer defaultYear) {
        if (item == null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "空条目");
        }
        Integer year = item.sourceYear() != null ? item.sourceYear() : defaultYear;
        if (isBlank(item.stem())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "stem: 题干不能为空");
        }
        if (item.stem().trim().length() > MAX_STEM_LENGTH) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID,
                    "stem: 题干超过 " + MAX_STEM_LENGTH + " 字");
        }
        if (isBlank(item.type())) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "type: 题型不能为空");
        }

        // 条目可自带状态:PDF 识别里由模型代答、或卷面根本没印答案的题目会带 DRAFT
        String status = "DRAFT".equalsIgnoreCase(item.status()) ? "DRAFT" : "PUBLISHED";

        // 复用单题录入的同一套校验与规范化规则。
        // 状态必须原样传进去:校验器要靠它决定「客观题没答案」能不能放行——DRAFT 放行,其余拒绝。
        QuestionUpsertDTO asUpsert = new QuestionUpsertDTO(
                subject.getId(), null, item.type().trim().toUpperCase(),
                item.stem().trim(), item.options(), item.answer(), item.answerPoints(),
                item.analysis(), item.difficulty(), defaultSource, year,
                item.score() == null ? new BigDecimal("2.0") : item.score(), status);
        QuestionValidator.Normalized n = QuestionValidator.validateAndNormalize(asUpsert);

        Question q = new Question();
        q.setSubjectId(subject.getId());
        q.setChapterId(resolveChapter(subject.getId(), item.chapterName()));
        q.setType(n.type().name());
        q.setStem(asUpsert.stem());
        q.setOptions(n.options());
        q.setAnswer(n.answer());
        q.setAnswerPoints(n.answerPoints());
        q.setAnalysis(item.analysis());
        q.setDifficulty(item.difficulty() == null ? (byte) 2 : item.difficulty().byteValue());
        q.setSource(defaultSource);
        q.setSourceYear(year);
        q.setScore(asUpsert.score());
        q.setStatus(status);
        return q;
    }

    /** 按名在该科目下找章节;找不到返回 null(留空,由导入结果提示)。 */
    private Long resolveChapter(Long subjectId, String chapterName) {
        if (isBlank(chapterName)) {
            return null;
        }
        List<Chapter> found = chapterRepository.findBySubjectIdAndName(subjectId, chapterName.trim());
        if (found.isEmpty()) {
            return null;
        }
        return found.get(0).getId();
    }

    private Long createPaper(Long subjectId, ImportPaperDTO dto,
                             List<ImportItemDTO> items, List<Question> savedParallel) {
        Paper paper = new Paper();
        paper.setSubjectId(subjectId);
        paper.setName(dto.name().trim());
        paper.setType(isBlank(dto.type()) ? "REAL" : dto.type().trim().toUpperCase());
        paper.setYear(dto.year());
        paper.setTotalScore(dto.totalScore() == null ? 100 : dto.totalScore());
        paper.setDurationMin(dto.durationMin() == null ? 150 : dto.durationMin());
        paper.setRemark(trimTo(dto.remark(), 255));
        Paper savedPaper = paperRepository.save(paper);

        // sort_order 用文件顺序(不是大题内顺序):回看试卷时能原样还原,
        // 大题靠 section_name 分组显示即可
        int sortOrder = 0;
        for (int i = 0; i < items.size(); i++) {
            Question q = savedParallel.get(i);
            if (q == null) {
                continue;   // 失败条目跳过,但仍占文件顺序,不补洞
            }
            sortOrder++;
            PaperQuestion pq = new PaperQuestion();
            pq.setPaperId(savedPaper.getId());
            pq.setQuestionId(q.getId());
            pq.setSectionName(trimTo(items.get(i).sectionName(), 64));
            pq.setSortOrder(sortOrder);
            paperQuestionRepository.save(pq);
        }
        return savedPaper.getId();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimTo(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "未知";
        }
        int idx = s.indexOf('\n');
        return idx < 0 ? s : s.substring(0, idx);
    }
}