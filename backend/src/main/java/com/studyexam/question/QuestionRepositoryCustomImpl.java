package com.studyexam.question;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 题目列表的动态查询。
 *
 * <p>关键字检索优先走 {@code ft_question_stem} 的 ngram 全文索引;若该索引在目标 MySQL 上
 * 不可用(如未启用 ngram 解析器),自动退回 {@code LIKE},并记录一次警告。宁可慢一点,
 * 也不能出现「搜不到题」。
 */
public class QuestionRepositoryCustomImpl implements QuestionRepositoryCustom {

    private static final Logger log = LoggerFactory.getLogger(QuestionRepositoryCustomImpl.class);

    /** 只允许这几个字段排序,避免把请求参数直接拼进 SQL 造成注入。 */
    private static final Map<String, String> SORTABLE = Map.of(
            "id", "q.id",
            "createdAt", "q.created_at",
            "updatedAt", "q.updated_at",
            "sourceYear", "q.source_year",
            "difficulty", "q.difficulty",
            "score", "q.score"
    );

    @PersistenceContext
    private EntityManager em;

    /** 全文索引不可用时置位,避免每次查询都白试一遍。 */
    private volatile boolean fulltextUnavailable = false;

    @Override
    public Page<Question> search(QuestionQuery query, Pageable pageable) {
        if (query.keyword() != null && !query.keyword().isBlank() && !fulltextUnavailable) {
            try {
                return doSearch(query, pageable, true);
            } catch (Exception ex) {
                fulltextUnavailable = true;
                log.warn("题干全文索引不可用,后续关键字检索将退回 LIKE。原因:{}", ex.getMessage());
                // 清掉可能已损坏的事务状态,重投一次 LIKE 查询
                em.clear();
            }
        }
        return doSearch(query, pageable, false);
    }

    private Page<Question> doSearch(QuestionQuery query, Pageable pageable, boolean useFulltext) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();

        appendEquals(where, params, "q.subject_id", query.subjectId());
        appendEquals(where, params, "q.chapter_id", query.chapterId());
        appendEquals(where, params, "q.type", query.type());
        appendEquals(where, params, "q.source_year", query.sourceYear());
        appendEquals(where, params, "q.status", query.status());

        if (query.keyword() != null && !query.keyword().isBlank()) {
            if (useFulltext) {
                where.append(" AND MATCH(q.stem) AGAINST (? IN BOOLEAN MODE) ");
                params.add(toBooleanMode(query.keyword()));
            } else {
                where.append(" AND q.stem LIKE ? ");
                params.add("%" + escapeLike(query.keyword()) + "%");
            }
        }

        String orderBy = buildOrderBy(pageable.getSort());

        Query countQuery = em.createNativeQuery("SELECT COUNT(*) FROM question q" + where);
        Query dataQuery = em.createNativeQuery("SELECT * FROM question q" + where + orderBy, Question.class);

        for (int i = 0; i < params.size(); i++) {
            countQuery.setParameter(i + 1, params.get(i));
            dataQuery.setParameter(i + 1, params.get(i));
        }
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Question> rows = dataQuery.getResultList();
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(rows, pageable, total);
    }

    private void appendEquals(StringBuilder where, List<Object> params, String column, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        where.append(" AND ").append(column).append(" = ? ");
        params.add(value);
    }

    /** 把用户输入拆成若干「词*」前缀项,BOOLEAN MODE 下对中文 ngram 更宽容。 */
    private String toBooleanMode(String keyword) {
        String[] parts = keyword.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            // 去掉 BOOLEAN MODE 的保留符号,避免用户输入 + - * " 等造成语法错误
            String cleaned = p.replaceAll("[+\\-><()~*\"@]", "").trim();
            if (!cleaned.isEmpty()) {
                sb.append(cleaned).append("* ");
            }
        }
        return sb.toString().trim();
    }

    private String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String buildOrderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return " ORDER BY q.id DESC ";
        }
        StringBuilder sb = new StringBuilder(" ORDER BY ");
        boolean first = true;
        for (Sort.Order order : sort) {
            String column = SORTABLE.get(order.getProperty());
            if (column == null) {
                continue;   // 未知排序字段直接忽略,不报错
            }
            if (!first) {
                sb.append(", ");
            }
            sb.append(column).append(order.isAscending() ? " ASC " : " DESC ");
            first = false;
        }
        if (first) {
            return " ORDER BY q.id DESC ";
        }
        sb.append(", q.id DESC ");
        return sb.toString();
    }
}