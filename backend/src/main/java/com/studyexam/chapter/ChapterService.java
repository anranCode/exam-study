package com.studyexam.chapter;

import com.studyexam.chapter.dto.ChapterUpsertDTO;
import com.studyexam.chapter.dto.ChapterVO;
import com.studyexam.common.BusinessException;
import com.studyexam.common.ErrorCode;
import com.studyexam.subject.SubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final SubjectService subjectService;

    public ChapterService(ChapterRepository chapterRepository, SubjectService subjectService) {
        this.chapterRepository = chapterRepository;
        this.subjectService = subjectService;
    }

    @Transactional(readOnly = true)
    public Chapter findOrThrow(Long id) {
        return chapterRepository.findById(id)
                .orElseThrow(() -> BusinessException.of(ErrorCode.CHAPTER_NOT_FOUND, "章节不存在:id=" + id));
    }

    @Transactional(readOnly = true)
    public List<ChapterVO> listBySubject(Long subjectId) {
        return subjectService.listChapters(subjectId);
    }

    @Transactional(readOnly = true)
    public ChapterVO get(Long id) {
        Chapter chapter = findOrThrow(id);
        return new ChapterVO(chapter.getId(), chapter.getSubjectId(), chapter.getParentId(),
                chapter.getName(), chapter.getSortOrder(),
                chapterRepository.countQuestionsUnderChapter(id));
    }

    @Transactional
    public ChapterVO create(ChapterUpsertDTO dto) {
        subjectService.findOrThrow(dto.subjectId());
        validateParent(dto.subjectId(), dto.parentId().orElse(null), null);

        Chapter chapter = new Chapter();
        chapter.setSubjectId(dto.subjectId());
        chapter.setName(dto.name().trim());
        chapter.setParentId(dto.parentId().orElse(null));
        chapter.setSortOrder(dto.sortOrder() == null ? 0 : dto.sortOrder());

        Chapter saved = chapterRepository.save(chapter);
        return new ChapterVO(saved.getId(), saved.getSubjectId(), saved.getParentId(),
                saved.getName(), saved.getSortOrder(), 0L);
    }

    @Transactional
    public ChapterVO update(Long id, ChapterUpsertDTO dto) {
        Chapter chapter = findOrThrow(id);
        subjectService.findOrThrow(dto.subjectId());

        // Optional 语义:缺省 = 不动父章节;显式 null = 升为顶级
        Optional<Long> incomingParent = dto.parentId();
        Long newParentId = incomingParent.orElse(chapter.getParentId());
        if (incomingParent.isPresent()) {
            validateParent(dto.subjectId(), incomingParent.orElse(null), id);
        } else if (newParentId != null) {
            validateParent(dto.subjectId(), newParentId, id);
        }

        chapter.setSubjectId(dto.subjectId());
        chapter.setName(dto.name().trim());
        chapter.setParentId(newParentId);
        if (dto.sortOrder() != null) {
            chapter.setSortOrder(dto.sortOrder());
        }

        Chapter saved = chapterRepository.save(chapter);
        return new ChapterVO(saved.getId(), saved.getSubjectId(), saved.getParentId(),
                saved.getName(), saved.getSortOrder(),
                chapterRepository.countQuestionsUnderChapter(id));
    }

    @Transactional
    public void delete(Long id) {
        Chapter chapter = findOrThrow(id);

        if (chapterRepository.existsByParentId(id)) {
            throw BusinessException.of(ErrorCode.CHAPTER_HAS_CONTENT,
                    "「" + chapter.getName() + "」下还有子章节,请先删除子章节");
        }
        long questionCount = chapterRepository.countQuestionsUnderChapter(id);
        if (questionCount > 0) {
            throw BusinessException.of(ErrorCode.CHAPTER_HAS_CONTENT,
                    "「" + chapter.getName() + "」下还有 " + questionCount + " 道题目,请先移走或删除");
        }
        chapterRepository.delete(chapter);
    }

    /**
     * 父章节必须:属于同一科目、不是自己、且自身是顶级章节(只支持两级)。
     */
    private void validateParent(Long subjectId, Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "父章节不能是自己");
        }
        Chapter parent = findOrThrow(parentId);
        if (!parent.getSubjectId().equals(subjectId)) {
            throw BusinessException.of(ErrorCode.CHAPTER_SUBJECT_MISMATCH,
                    "父章节「" + parent.getName() + "」不属于该科目");
        }
        if (parent.getParentId() != null) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "章节只支持两级,不能在子章节下再建子章节");
        }
    }
}