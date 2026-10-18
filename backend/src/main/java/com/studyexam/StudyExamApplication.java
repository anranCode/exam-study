package com.studyexam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 自考学习中心后端入口。
 *
 * <p>{@code @EnableScheduling} 是给 {@code ReminderScheduler} 用的:它每分钟检查一次
 * 是否到了早间/晚间的提醒时间。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class StudyExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyExamApplication.class, args);
    }
}