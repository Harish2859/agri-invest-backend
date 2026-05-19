package com.agriinvest.platform.repository;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Investment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    // For the Investor Portfolio
    List<Investment> findByInvestorId(Long investorId);
    List<Investment> findByInvestorIdAndStatus(Long investorId, String status);

    // For the Farmer's Project View
    List<Investment> findByProjectId(Long projectId);

    List<Investment> findByProjectIdAndStatus(Long projectId, String status);

    // CRITICAL: Only completed payments should count toward raised capital
    @Query("SELECT COALESCE(SUM(i.amountInvested), 0) FROM Investment i " +
            "WHERE i.project.id = :projectId AND i.status = 'COMPLETED'")
    Double getActualRaisedAmount(@Param("projectId") Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FarmProject p WHERE p.id = :id")
    Optional<FarmProject> findByIdWithLock(@Param("id") Long id);
}
