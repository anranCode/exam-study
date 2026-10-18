package com.studyexam.question;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 题目列表查询。关键字检索走 ngram 全文索引,索引不可用时自动退回 LIKE,
 * 保证「能搜到」优先于「搜得最快」。
 */
public interface QuestionRepositoryCustom {

    Page<Question> search(QuestionQuery query, Pageable pageable);
}