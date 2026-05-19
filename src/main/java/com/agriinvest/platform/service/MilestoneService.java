package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Milestone;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.repository.MilestoneRepository;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MilestoneService {

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Creates a new milestone with a strict check:
     * The total percentage for a project cannot exceed 100%.
     */
    @Transactional
    public Milestone createMilestone(Milestone milestone, String farmerEmail) {
        if (milestone.getFarmProject() == null || milestone.getFarmProject().getId() == null) {
            throw new IllegalArgumentException("Milestone must be linked to a project.");
        }

        Long projectId = milestone.getFarmProject().getId();
        FarmProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (project.getFarmer() == null || project.getFarmer().getEmail() == null
                || !project.getFarmer().getEmail().equalsIgnoreCase(farmerEmail)) {
            throw new IllegalStateException("You can only create milestones for your own projects.");
        }

        milestone.setFarmProject(project);

        // 1. Calculate current total percentage
        Double currentTotal = milestoneRepository.getTotalPercentageByProject(projectId);
        if (currentTotal == null) currentTotal = 0.0;

        // 2. The 100% Rule Check
        if (currentTotal + milestone.getReleasePercentage() > 100.0) {
            double allowed = 100.0 - currentTotal;
            throw new IllegalArgumentException(
                    "Milestone exceeds budget! Only " + String.format("%.1f%%", allowed) + " remaining."
            );
        }

        // 3. Set default status if not provided
        if (milestone.getStatus() == null) {
            milestone.setStatus("PENDING_SUBMISSION");
        }

        return milestoneRepository.save(milestone);
    }

    @Transactional(readOnly = true)
    public Milestone getMilestoneForFarmer(Long milestoneId, String farmerEmail) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new RuntimeException("Milestone not found"));

        FarmProject project = milestone.getFarmProject();
        if (project == null || project.getFarmer() == null || project.getFarmer().getEmail() == null
                || !project.getFarmer().getEmail().equalsIgnoreCase(farmerEmail)) {
            throw new IllegalStateException("You can only modify milestones for your own projects.");
        }

        return milestone;
    }

    @Transactional(readOnly = true)
    public List<Milestone> getMilestonesByStatus(String status) {
        return milestoneRepository.findByStatus(status);
    }

    @Transactional
    public void generateDefaultMilestones(FarmProject project) {
        if (project == null || project.getId() == null) {
            throw new IllegalArgumentException("Project is required to generate milestones.");
        }

        if (!milestoneRepository.findByFarmProjectId(project.getId()).isEmpty()) {
            return;
        }

        List<Milestone> defaults = new ArrayList<>();
        defaults.add(defaultMilestone(project, "Land Preparation", 25.0));
        defaults.add(defaultMilestone(project, "Sowing", 25.0));
        defaults.add(defaultMilestone(project, "Crop Maintenance", 25.0));
        defaults.add(defaultMilestone(project, "Harvest & Delivery", 25.0));
        milestoneRepository.saveAll(defaults);
    }

    @Transactional
    public Milestone verifyMilestone(Long id) {
        return approveMilestone(id);
    }

    /**
     * Approves a milestone and moves funds from project escrow to farmer wallet.
     * Flow:
     * 1) Fetch milestone + project + farmer
     * 2) project.escrowBalance -= milestone amount
     * 3) farmer.walletBalance += milestone amount
     * 4) Mark milestone completed and persist all
     */
    @Transactional
    public Milestone approveMilestone(Long milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new RuntimeException("Milestone not found"));

        if (milestone.isFundsReleased() || "COMPLETED".equalsIgnoreCase(milestone.getStatus())) {
            return milestone;
        }

        if (milestone.getFarmProject() == null || milestone.getFarmProject().getId() == null) {
            throw new IllegalStateException("Milestone is not linked to a valid project.");
        }

        FarmProject project = projectRepository.findByIdWithLock(milestone.getFarmProject().getId())
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getFarmer() == null || project.getFarmer().getId() == null) {
            throw new IllegalStateException("Project has no valid farmer.");
        }

        User farmer = userRepository.findById(project.getFarmer().getId())
                .orElseThrow(() -> new RuntimeException("Farmer not found"));

        BigDecimal escrowBalance = nvl(project.getEscrowBalance());
        BigDecimal releaseAmount = resolveReleaseAmount(milestone, project);
        if (releaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Milestone release amount must be greater than zero.");
        }
        if (escrowBalance.compareTo(releaseAmount) < 0) {
            throw new IllegalStateException("Insufficient escrow balance for milestone release.");
        }

        project.setEscrowBalance(escrowBalance.subtract(releaseAmount));
        project.setReleasedToFarmer(nvl(project.getReleasedToFarmer()).add(releaseAmount));
        project.setWithdrawableBalance(nvl(project.getWithdrawableBalance()).add(releaseAmount));
        farmer.setWalletBalance(nvl(farmer.getWalletBalance()).add(releaseAmount));

        milestone.setVerified(true);
        milestone.setStatus("COMPLETED");
        milestone.setVerifiedAt(LocalDateTime.now());
        milestone.setFundsReleased(true);
        milestone.setAmountToRelease(releaseAmount.doubleValue());

        userRepository.save(farmer);
        projectRepository.save(project);
        return milestoneRepository.save(milestone);
    }

    @Transactional
    public Milestone verifyAndRelease(Long milestoneId, String leadEmail) {
        User lead = null;
        if (leadEmail != null && !leadEmail.isBlank()) {
            lead = userRepository.findByEmail(leadEmail)
                    .orElseThrow(() -> new RuntimeException("Lead not found"));
        }

        Milestone milestone = approveMilestone(milestoneId);
        milestone.setVerifiedBy(lead);
        return milestoneRepository.save(milestone);
    }

    @Transactional
    public Milestone rejectMilestone(Long milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new RuntimeException("Milestone not found"));

        milestone.setStatus("REJECTED");
        milestone.setVerified(false);
        milestone.setVerifiedAt(null);
        milestone.setVerifiedBy(null);
        milestone.setFundsReleased(false);

        return milestoneRepository.save(milestone);
    }

    private BigDecimal resolveReleaseAmount(Milestone milestone, FarmProject project) {
        if (milestone.getAmountToRelease() != null && milestone.getAmountToRelease() > 0) {
            return bd(milestone.getAmountToRelease());
        }

        if (project.getTargetAmount() != null && milestone.getReleasePercentage() != null && milestone.getReleasePercentage() > 0) {
            return project.getTargetAmount()
                    .multiply(percent(milestone.getReleasePercentage()))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal percent(Double value) {
        return bd(value).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal bd(Double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Milestone defaultMilestone(FarmProject project, String title, double releasePercentage) {
        Milestone milestone = new Milestone();
        milestone.setFarmProject(project);
        milestone.setTitle(title);
        milestone.setStatus("PENDING_SUBMISSION");
        milestone.setReleasePercentage(releasePercentage);
        return milestone;
    }
}
