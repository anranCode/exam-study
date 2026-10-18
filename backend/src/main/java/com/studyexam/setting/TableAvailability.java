package com.studyexam.setting;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据表存在性探测。
 *
 * <p>本项目的实现分阶段落地,有些表要等到后面的阶段才建(如 {@code user_setting} 在阶段 7)。
 * 为了不让「今日页」这类跨阶段的接口整体报错,读取这些表前先用本类探测一次,表不存在时
 * 由调用方给出默认值。这样前端在任何阶段都能拿到结构完整、数值合法的响应。
 *
 * <p>探测结果按进程缓存:表一旦建好就不会消失,而 Flyway 迁移发生在应用启动之前。
 * 这样热路径(如今日页同时要读 study_log 与 user_setting)不会反复查 information_schema。
 */
@Component
public class TableAvailability {

    private static final Logger log = LoggerFactory.getLogger(TableAvailability.class);

    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    @PersistenceContext
    private EntityManager em;

    public boolean exists(String tableName) {
        return cache.computeIfAbsent(tableName, this::probe);
    }

    private boolean probe(String tableName) {
        try {
            Object result = em.createNativeQuery(
                            "SELECT COUNT(*) FROM information_schema.tables "
                                    + "WHERE table_schema = DATABASE() AND table_name = ?1")
                    .setParameter(1, tableName)
                    .getSingleResult();
            boolean present = ((Number) result).longValue() > 0;
            if (!present) {
                log.debug("表 {} 尚未建立,依赖它的指标将返回默认值", tableName);
            }
            return present;
        } catch (Exception ex) {
            log.debug("探测表 {} 是否存在时失败:{}", tableName, ex.getMessage());
            return false;
        }
    }
}