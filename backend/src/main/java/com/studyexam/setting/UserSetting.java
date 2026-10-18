package com.studyexam.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 用户设置。单用户应用,表里只有一行(user_id = 1)。
 *
 * <p>字段与 V5 迁移逐列对应。{@code notifyWebhook} / {@code notifyKey} 是密钥类字段,
 * 任何接口都只返回掩码。
 */
@Entity
@Table(name = "user_setting")
@Getter
@Setter
@NoArgsConstructor
public class UserSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer dailyQuestionCount = 10;

    /** WECOM / SERVERCHAN */
    @Column(nullable = false, length = 16)
    private String notifyChannel = "WECOM";

    @Column(length = 512)
    private String notifyWebhook;

    @Column(length = 255)
    private String notifyKey;

    @Column(nullable = false)
    private Boolean reminderEnabled = true;

    @Column(nullable = false)
    private LocalTime reminderTime = LocalTime.of(7, 0);

    @Column(nullable = false)
    private Boolean eveningReminderEnabled = true;

    @Column(nullable = false)
    private LocalTime eveningReminderTime = LocalTime.of(21, 30);

    /** AUTO / LIGHT / DARK */
    @Column(nullable = false, length = 16)
    private String darkMode = "AUTO";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
