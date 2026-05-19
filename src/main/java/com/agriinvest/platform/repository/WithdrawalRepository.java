package com.agriinvest.platform.repository;

import com.agriinvest.platform.entity.Withdrawal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, Long> {
    List<Withdrawal> findByProjectId(Long projectId);
    List<Withdrawal> findByProjectFarmerEmailOrderByRequestedAtDesc(String email);
    List<Withdrawal> findByUserEmailOrderByRequestedAtDesc(String email);

    @Query("SELECT SUM(w.amount) FROM Withdrawal w WHERE w.project.farmer.id = :farmerId AND w.status = 'COMPLETED'")
    Double getTotalWithdrawnByFarmer(@Param("farmerId") Long farmerId);
}
