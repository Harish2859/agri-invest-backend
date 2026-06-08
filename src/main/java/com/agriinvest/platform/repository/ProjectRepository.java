package com.agriinvest.platform.repository;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.ProjectStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<FarmProject, Long> {
    List<FarmProject> findByFarmerId(Long farmerId);
    List<FarmProject> findByFarmerIdAndStatus(Long farmerId, ProjectStatus status);
    List<FarmProject> findByCropTypeContainingIgnoreCase(String cropType);
    List<FarmProject> findByStatus(ProjectStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FarmProject p WHERE p.id = :id")
    Optional<FarmProject> findByIdWithLock(@Param("id") Long id);

    // Advanced Discovery Query
    // Filters by location, crop, and ensures only 'OPEN' projects are shown
    @Query("SELECT p FROM FarmProject p WHERE " +
            "(:location IS NULL OR LOWER(CAST(p.location AS string)) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))) AND " +
            "(:crop IS NULL OR LOWER(CAST(p.cropType AS string)) LIKE LOWER(CONCAT('%', CAST(:crop AS string), '%'))) AND " +
            "(p.status = :status)")
    List<FarmProject> discoverProjects(
            @Param("location") String location,
            @Param("crop") String crop,
            @Param("status") ProjectStatus status
    );

    default List<FarmProject> discoverProjects(String location, String crop) {
        return discoverProjects(location, crop, ProjectStatus.FUNDING_IN_PROGRESS);
    }
}
