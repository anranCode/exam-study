package com.studyexam.importer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportLogRepository extends JpaRepository<ImportLog, Long> {

    /** 最近的导入记录排前面;同一秒内按 id 兜底,分页顺序才稳定。 */
    Page<ImportLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
