package com.agriinvest.platform.controller;

import com.agriinvest.platform.dto.SettlementRequest;
import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Milestone;
import com.agriinvest.platform.entity.ProjectStatus;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.repository.MilestoneRepository;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.UserRepository;
import com.agriinvest.platform.service.ProjectSettlementService;
import com.agriinvest.platform.service.ProjectService;
import com.agriinvest.platform.service.MilestoneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectSettlementService projectSettlementService;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private MilestoneService milestoneService;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('FARMER')")
    public ResponseEntity<?> createProject(@RequestBody FarmProject project, Authentication authentication) {
        User farmer = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Farmer not found"));

        if (!farmer.isVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Your account must be verified by a Lead before creating projects."));
        }

        project.setFarmer(farmer);
        FarmProject saved = projectService.createProject(project);
        return ResponseEntity.ok(saved);
    }

    /**
     * ADVANCED DISCOVERY (The All-India Marketplace)
     * Filters: ?region=Punjab&crop=Rice
     * Only shows projects where status is 'FUNDING_IN_PROGRESS'
     */
    @GetMapping("/discover")
    public ResponseEntity<List<Map<String, Object>>> discoverProjects(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String crop) {

        // Use the custom repository method we discussed (make sure it exists in ProjectRepository)
        List<FarmProject> availableProjects = projectRepository.discoverProjects(region, crop);

        List<Map<String, Object>> marketplace = availableProjects.stream().map(p -> {
            Map<String, Object> card = new HashMap<>();
            double raised = projectService.getAmountRaised(p.getId());
            double target = p.getTargetAmount().doubleValue();

            card.put("projectId", p.getId());
            card.put("title", p.getTitle());
            card.put("location", p.getLocation()); // Crucial for All-India context
            card.put("crop", p.getCropType());
            card.put("targetAmount", target);
            card.put("amountAlreadyRaised", raised);
            card.put("remainingAmount", target - raised);

            // Helpful percentage for the Investor UI
            card.put("fundingPercentage", target > 0 ?
                    String.format("%.1f%%", (raised / target) * 100) : "0%");

            card.put("farmerName", p.getFarmer() != null ?
                    p.getFarmer().getFullName() : "Verified Farmer");

            return card;
        }).toList();

        return ResponseEntity.ok(marketplace);
    }

    // Keep this for simple internal lookups if needed
    @GetMapping("/all")
    public ResponseEntity<List<FarmProject>> getAllProjects() {
        List<FarmProject> projects = projectService.getAllProjects().stream()
                .map(this::attachResolvedFunding)
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/my-projects")
    @PreAuthorize("hasAuthority('FARMER')")
    public ResponseEntity<List<FarmProject>> getMyProjects(Authentication authentication) {
        List<FarmProject> projects = projectService.getProjectsByCurrentFarmer(authentication.getName()).stream()
                .map(this::attachResolvedFunding)
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/user/me")
    @PreAuthorize("hasAuthority('FARMER')")
    public ResponseEntity<List<FarmProject>> getMyProjectsForLegacyClient(Authentication authentication) {
        List<FarmProject> projects = projectService.getProjectsByCurrentFarmer(authentication.getName()).stream()
                .map(this::attachResolvedFunding)
                .collect(Collectors.toList());
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProjectById(@PathVariable Long id) {
        return projectService.findById(id)
                .map(this::attachResolvedFunding)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAuthority('FARMER')")
    public ResponseEntity<?> settleProject(@PathVariable Long id,
                                           @RequestBody SettlementRequest request,
                                           Authentication authentication) {
        FarmProject project = projectService.findById(id)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        String currentUserEmail = authentication.getName();
        String projectFarmerEmail = project.getFarmer() != null ? project.getFarmer().getEmail() : null;
        if (projectFarmerEmail == null || !projectFarmerEmail.equalsIgnoreCase(currentUserEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You can only settle your own project."));
        }

        try {
            FarmProject settledProject = projectSettlementService.settleProject(id, request.getTotalRevenue());
            return ResponseEntity.ok(Map.of(
                    "projectId", settledProject.getId(),
                    "status", settledProject.getStatus(),
                    "finalRevenue", request.getTotalRevenue(),
                    "message", "Project settled successfully."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/active")
    public List<FarmProject> getActiveProjects() {
        return projectRepository.findByStatus(ProjectStatus.FUNDING_IN_PROGRESS).stream()
                .map(this::attachResolvedFunding)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/milestones")
    public ResponseEntity<List<Milestone>> getProjectMilestones(@PathVariable Long id) {
        return ResponseEntity.ok(milestoneRepository.findByFarmProjectId(id));
    }

    @PostMapping("/{id}/reconcile")
    @PreAuthorize("hasAnyAuthority('FARMER','VILLAGE_LEAD')")
    public ResponseEntity<?> reconcileProject(@PathVariable Long id) {
        FarmProject project = projectService.reconcileProjectFundingState(id, milestoneService);
        return ResponseEntity.ok(Map.of(
                "projectId", project.getId(),
                "status", project.getStatus(),
                "currentFunding", project.getCurrentFunding(),
                "message", "Project funding state reconciled."
        ));
    }

    private FarmProject attachResolvedFunding(FarmProject project) {
        project.setCurrentFunding(BigDecimal.valueOf(projectService.getAmountRaised(project.getId())));
        return project;
    }
}
