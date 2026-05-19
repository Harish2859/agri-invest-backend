package com.agriinvest.platform.repository;

import com.agriinvest.platform.entity.ProjectUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectUpdateRepository extends JpaRepository<ProjectUpdate, Long> {
    // Get updates for a project, newest first
    List<ProjectUpdate> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}