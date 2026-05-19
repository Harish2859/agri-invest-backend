package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.ProjectUpdate;
import com.agriinvest.platform.repository.ProjectUpdateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/updates")
public class ProjectUpdateController {

    @Autowired
    private ProjectUpdateRepository updateRepository;

    @PostMapping("/post")
    public ProjectUpdate postUpdate(@RequestBody ProjectUpdate update) {
        update.setCreatedAt(LocalDateTime.now());
        return updateRepository.save(update);
    }

    @GetMapping("/project/{projectId}")
    public List<ProjectUpdate> getUpdates(@PathVariable Long projectId) {
        return updateRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }
}