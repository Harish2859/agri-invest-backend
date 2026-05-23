package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.KycStatus;
import com.agriinvest.platform.entity.Milestone;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.repository.UserRepository;
import com.agriinvest.platform.service.MilestoneService;
import com.agriinvest.platform.service.NotificationService;
import com.agriinvest.platform.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fully synchronized AdminController for Village Lead Governance.
 * All methods now use hasAuthority to match database role strings precisely.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ProjectService projectService;
    private final MilestoneService milestoneService;

    public AdminController(UserRepository userRepository,
                           NotificationService notificationService,
                           ProjectService projectService,
                           MilestoneService milestoneService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.projectService = projectService;
        this.milestoneService = milestoneService;
    }

    // Fixed: Standardized to hasAuthority
    @GetMapping({"/pending-kyc", "/pending-farmers"})
    @PreAuthorize("hasAuthority('VILLAGE_LEAD')")
    public ResponseEntity<List<PendingFarmerView>> getPendingKyc() {
        List<PendingFarmerView> pendingFarmers = userRepository
                .findByKycStatusAndRoleOrderByCreatedAtAsc(KycStatus.SUBMITTED, User.Role.FARMER)
                .stream()
                .map(user -> new PendingFarmerView(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getAadhaarNo(),
                        user.getKycDocumentUrl(),
                        user.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(pendingFarmers);
    }

    // Fixed: Changed from hasRole to hasAuthority
    @GetMapping("/pending-projects")
    @PreAuthorize("hasAuthority('VILLAGE_LEAD')")
    public ResponseEntity<List<FarmProject>> getPendingProjects() {
        return ResponseEntity.ok(projectService.getProjectsByStatus("PENDING"));
    }

    @PostMapping("/approve-project/{id}")
    @PreAuthorize("hasAuthority('VILLAGE_LEAD')")
    public ResponseEntity<?> approveProject(@PathVariable Long id) {
        FarmProject approvedProject = projectService.approveProject(id);
        return ResponseEntity.ok(Map.of(
                "projectId", approvedProject.getId(),
                "status", approvedProject.getStatus(),
                "message", "Project approved and moved to funding."
        ));
    }

    @GetMapping("/pending-milestones")
    @PreAuthorize("hasAuthority('VILLAGE_LEAD')")
    public ResponseEntity<List<Milestone>> getPendingMilestones() {
        return ResponseEntity.ok(milestoneService.getMilestonesByStatus("SUBMITTED"));
    }

    // Fixed: Changed from hasRole to hasAuthority
    @PostMapping("/verify-farmer/{id}")
    @PreAuthorize("hasAuthority('VILLAGE_LEAD')")
    public ResponseEntity<?> verifyFarmer(
            @PathVariable Long id,
            @RequestParam(name = "approve", defaultValue = "true") boolean approve
    ) {
        User farmer = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Farmer not found"));

        if (farmer.getRole() != User.Role.FARMER) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only farmers can be verified"));
        }

        farmer.setVerified(approve);
        farmer.setKycStatus(approve ? KycStatus.APPROVED : KycStatus.REJECTED);
        farmer.setKycVerifiedAt(approve ? LocalDateTime.now() : null);
        if (approve) {
            farmer.setKycRejectionReason(null);
        } else {
            farmer.setKycRejectionReason("Verification rejected by village lead. Please re-submit clear KYC documents.");
        }
        userRepository.save(farmer);

        if (approve) {
            notificationService.createNotification(
                    farmer.getEmail(),
                    "Your farmer account has been verified. You can now create projects."
            );
            return ResponseEntity.ok(Map.of("message", "Farmer " + farmer.getFullName() + " is now VERIFIED."));
        }

        notificationService.createNotification(
                farmer.getEmail(),
                "Your farmer verification request was rejected. Please review your submitted details."
        );
        return ResponseEntity.ok(Map.of("message", "Farmer verification rejected."));
    }

    // Support both legacy POST clients and newer PUT clients for the same action
    @RequestMapping(value = "/verify-user/{id}", method = {RequestMethod.POST, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('VILLAGE_LEAD')")
    public ResponseEntity<?> verifyUser(
            @PathVariable Long id,
            @RequestParam(name = "approve", defaultValue = "true") boolean approve
    ) {
        return verifyFarmer(id, approve);
    }

    public record PendingFarmerView(
            Long id,
            String fullName,
            String email,
            String aadhaarNo,
            String kycDocumentUrl,
            LocalDateTime createdAt
    ) {
    }
}
