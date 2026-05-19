package com.agriinvest.platform.repository;

import com.agriinvest.platform.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByFarmProjectId(Long farmProjectId);
    List<Milestone> findByStatus(String status);
    List<Milestone> findByStatusAndFarmProject_LocationContainingIgnoreCase(String status, String location);

    // This helps show the Lead's "Impact" (Total amount they have approved)
    @Query("SELECT SUM(m.amountToRelease) FROM Milestone m WHERE m.isVerified = true")
    Double getTotalVerifiedAmount();

    @Query("SELECT COUNT(m) FROM Milestone m WHERE m.farmProject.farmer.id = :farmerId AND m.status = 'SUBMITTED'")
    long countPendingVerificationsByFarmer(@Param("farmerId") Long farmerId);

    @Query("SELECT SUM(m.releasePercentage) FROM Milestone m WHERE m.farmProject.id = :projectId")
    Double getTotalPercentageByProject(@Param("projectId") Long projectId);

    @Query("SELECT m FROM Milestone m JOIN m.farmProject p WHERE m.status = :status AND LOWER(p.location) LIKE LOWER(CONCAT('%', :region, '%'))")
    List<Milestone> findByStatusAndLocation(@Param("status") String status, @Param("region") String region);

    Optional<Milestone> findTopByFarmProjectIdAndIsVerifiedTrueAndVerifiedByIsNotNullOrderByVerifiedAtDesc(Long farmProjectId);

}
