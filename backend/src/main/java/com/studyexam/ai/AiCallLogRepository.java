package com.studyexam.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    /** 近 N 天某场景的用量合计,用于「这轮识别花了多少 token」。 */
    @Query("""
            SELECT COALESCE(SUM(l.tokensIn), 0) FROM AiCallLog l
            WHERE l.userId = :userId AND l.scene = :scene AND l.createdAt >= :from
            """)
    long sumTokensInSince(@Param("userId") Long userId,
                          @Param("scene") String scene,
                          @Param("from") LocalDateTime from);

    @Query("""
            SELECT COALESCE(SUM(l.tokensOut), 0) FROM AiCallLog l
            WHERE l.userId = :userId AND l.scene = :scene AND l.createdAt >= :from
            """)
    long sumTokensOutSince(@Param("userId") Long userId,
                           @Param("scene") String scene,
                           @Param("from") LocalDateTime from);
}