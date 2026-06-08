package com.agriinvest.platform.repository;

import com.agriinvest.platform.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {
    List<TransactionRecord> findByUserIdOrderByLocalizedTimestampDesc(Long userId);
}
