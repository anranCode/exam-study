package com.studyexam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关。
 *
 * <p>提醒是本项目的「刚需」:由 {@code ReminderScheduler} 每分钟检查一次,
 * 到点经微信通道发送「今日 N 题待复习」。调度器在阶段 7 实现。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}