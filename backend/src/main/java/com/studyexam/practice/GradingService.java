package com.studyexam.practice;

import com.studyexam.question.Question;
import com.studyexam.question.QuestionType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 判分。
 *
 * <p>客观题(单选、多选、判断、英语各客观题型):后端比对答案,直接判定正确与否。
 * 主观题(简答、论述、材料分析、写作):后端**不判分**,只返回参考答案要点,
 * 由前端让用户逐条勾选自评(见 01-architecture.md 第三节)。
 *
 * <p>比对前做规范化:忽略大小写、空白与中文标点;多选题按字母升序比较
 * (所以 "dba" 与 "ABD" 判为相同)。判断题接受「对/正确/T」与「错/错误/F」。
 */
@Component
public class GradingService {

    /** 该题是否由后端自动判分。 */
    public boolean isAutoGraded(Question question) {
        return QuestionType.find(question.getType()).map(QuestionType::isObjective).orElse(false);
    }

    /**
     * 客观题判分。
     *
     * <p>返回值:
     * <ul>
     *   <li><b>true / false</b> —— 客观题判定结果;</li>
     *   <li><b>null</b> —— 不判分:主观题,或者题目缺少标准答案且用户确实作答了
     *       (此时把对的判成错的更糟,交给人工处理)。</li>
     * </ul>
     *
     * <p>空作答对客观题一定是错的,即使这道题缺标准答案也一样 ——
     * 「不知道」正是靠这条把题记进错题本,否则一道脏数据题会永远进不了错题本。
     */
    public Boolean grade(Question question, String userAnswer) {
        if (!isAutoGraded(question)) {
            return null;
        }
        if (userAnswer == null || userAnswer.isBlank()) {
            return false;
        }
        String standard = question.getAnswer();
        if (standard == null || standard.isBlank()) {
            return null;
        }

        QuestionType type = QuestionType.find(question.getType()).orElse(null);
        String expected = normalize(standard, type);
        String actual = normalize(userAnswer, type);
        return !actual.isEmpty() && expected.equals(actual);
    }

    /**
     * 规范化答案文本。多选题按字母升序排列后再比较。
     */
    public String normalize(String raw, QuestionType type) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().toUpperCase()
                .replaceAll("[\\s,，、;；]", "")
                .replaceAll("[()（）]", "");
        // 判断题的中文写法归一
        if (type == QuestionType.TRUE_FALSE || type == QuestionType.READING_JUDGE) {
            if (s.equals("对") || s.equals("正确") || s.equals("√") || s.equals("是")) {
                return "T";
            }
            if (s.equals("错") || s.equals("错误") || s.equals("×") || s.equals("否")) {
                return "F";
            }
        }
        if (type == QuestionType.MULTIPLE_CHOICE && s.length() > 1) {
            return sortLetters(s);
        }
        return s;
    }

    /** 把 "DBA" 规范化为 "ABD"(去重 + 升序)。 */
    public String sortLetters(String letters) {
        List<String> chars = letters.chars()
                .mapToObj(c -> String.valueOf((char) c))
                .filter(c -> !c.isBlank())
                .distinct()
                .sorted()
                .toList();
        return String.join("", chars);
    }
}