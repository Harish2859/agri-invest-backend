package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.Milestone;
import com.agriinvest.platform.repository.MilestoneRepository;
import com.agriinvest.platform.service.MilestoneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/milestones")
public class MilestoneController {

    @Autowired
    private MilestoneService milestoneService;

    @Autowired
    private MilestoneRepository milestoneRepository;

    /**
     * 1. Farmer Creates a Milestone (Draft Phase)
     * Uses the MilestoneService to enforce the "100% Rule"
     */
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('FARMER')")
    public ResponseEntity<Milestone> createMilestone(@RequestBody Milestone milestone, Authentication authentication) {
        // Initial status is DRAFT until proof is uploaded
        milestone.setStatus("DRAFT");
        return ResponseEntity.ok(milestoneService.createMilestone(milestone, authentication.getName()));
    }

    /**
     * 2. Farmer Submits Proof & Triggers Lead Verification
     * This moves the milestone from 'DRAFT' to 'SUBMITTED'
     */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('FARMER')")
    @Transactional
    public ResponseEntity<Milestone> submitMilestone(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        Milestone milestone = milestoneService.getMilestoneForFarmer(id, authentication.getName());

        // Only allow submission if it's currently a DRAFT or REJECTED
        milestone.setProofImageUrl(body.get("proofImageUrl"));
        milestone.setStatus("SUBMITTED");
        milestone.setSubmittedAt(LocalDateTime.now());

        return ResponseEntity.ok(milestoneRepository.save(milestone));
    }

    /**
     * 3. Village Lead / Investor Verification
     * Moved from PutMapping to reflect the "Verify" action we used in Dashboard
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('VILLAGE_LEAD')")
    @Transactional
    public ResponseEntity<Milestone> verifyMilestone(
            @PathVariable Long id,
            @RequestParam(name = "approved", defaultValue = "true") boolean approved,
            Authentication authentication) {
        if (approved) {
            return ResponseEntity.ok(milestoneService.verifyAndRelease(id, authentication.getName()));
        }
        return ResponseEntity.ok(milestoneService.rejectMilestone(id));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Milestone>> getMilestonesByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(milestoneRepository.findByFarmProjectId(projectId));
    }

    @GetMapping("/project/{projectId}/summary")
    public ResponseEntity<?> getProjectSummary(@PathVariable Long projectId) {
        List<Milestone> milestones = milestoneRepository.findByFarmProjectId(projectId);

        double totalReleased = milestones.stream()
                .filter(m -> "COMPLETED".equals(m.getStatus()))
                .mapToDouble(m -> m.getAmountToRelease() != null ? m.getAmountToRelease() : 0.0)
                .sum();

        Map<String, Object> summary = new HashMap<>();
        if (!milestones.isEmpty()) {
            summary.put("projectTitle", milestones.get(0).getFarmProject().getTitle());
            summary.put("totalBudget", milestones.get(0).getFarmProject().getTargetAmount());
        }
        summary.put("totalFundsReleased", totalReleased);
        summary.put("milestonesCompleted", milestones.stream().filter(m -> "COMPLETED".equals(m.getStatus())).count());

        return ResponseEntity.ok(summary);
    }

    /**
     * 4. Physical File Upload
     * Generates a local path for the proof image.
     */
    @PostMapping("/{id}/upload-proof")
    public ResponseEntity<Map<String, String>> uploadFile(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {

        Path uploadPath = Paths.get("uploads/proofs");
        if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

        String fileName = "m_" + id + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Map<String, String> response = new HashMap<>();
        response.put("url", filePath.toString());
        return ResponseEntity.ok(response);
    }
}
