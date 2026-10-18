package com.studyexam.importer;

/**
 * 一条没能入库的题目。
 *
 * <p>{@code index} 是它在导入文件里的序号(从 1 开始);整份被拒(科目不存在、网络失败)时为空,
 * 这时只有 {@code reason}。{@code stem} 用来人工补录 —— 光有「第 12 条失败」没法知道是哪道题。
 */
public record ImportIssue(Integer index, String stem, String reason) {
}
