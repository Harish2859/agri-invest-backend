package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.*;
import com.agriinvest.platform.repository.*;
import com.agriinvest.platform.service.MilestoneService;
import com.agriinvest.platform.service.ProjectService;
import com.agriinvest.platform.service.InvestmentService;
import com.agriinvest.platform.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectUpdateRepository updateRepository;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private InvestmentService investmentService;

    @Autowired
    private MilestoneService milestoneService;

    @Autowired
    private UserService userService;

    // --- FARMER PORTFOLIO SECTION ---

    @GetMapping("/farmer/{farmerId}/portfolio")
    public Map<String, Object> getFarmerPortfolio(@PathVariable Long farmerId) {
        List<FarmProject> projects = projectRepository.findByFarmerId(farmerId);

        Double totalBudgetManaged = projects.stream().mapToDouble(p -> p.getTargetAmount().doubleValue()).sum();
        Double totalRaised = projects.stream().mapToDouble(p -> projectService.getAmountRaised(p.getId())).sum();
        Double totalWithdrawable = projects.stream().mapToDouble(p -> projectService.getWithdrawableAmount(p.getId())).sum();
        BigDecimal totalEarnings = userService.calculateTrueFarmerEarnings(farmerId);

        Double totalWithdrawn = withdrawalRepository.getTotalWithdrawnByFarmer(farmerId);
        if (totalWithdrawn == null) totalWithdrawn = 0.0;

        long pendingVerifications = milestoneRepository.countPendingVerificationsByFarmer(farmerId);
        long completedProjects = projects.stream().filter(p -> ProjectStatus.COMPLETED == p.getStatus()).count();
        long activeProjects = projects.stream().filter(p -> ProjectStatus.FUNDING_IN_PROGRESS == p.getStatus()).count();

        Map<String, Object> response = new HashMap<>();
        response.put("farmerId", farmerId);

        Map<String, Object> financials = new HashMap<>();
        financials.put("totalValueManaged", totalBudgetManaged);
        financials.put("totalActualRaised", totalRaised);
        financials.put("totalEarnings", totalEarnings);
        financials.put("withdrawableBalance", totalWithdrawable);
        financials.put("totalWithdrawnToDate", totalWithdrawn);
        response.put("financials", financials);

        Map<String, Object> alerts = new HashMap<>();
        alerts.put("pendingLeadVerifications", pendingVerifications);
        alerts.put("actionRequired", pendingVerifications > 0);
        response.put("alerts", alerts);

        response.put("myFarms", projects.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            double currentFunding = projectService.getAmountRaised(p.getId());
            double target = p.getTargetAmount() != null ? p.getTargetAmount().doubleValue() : 0.0;
            map.put("projectId", p.getId());
            map.put("title", p.getTitle());
            map.put("status", p.getStatus());
            map.put("raisedAmount", currentFunding);
            map.put("currentFunding", currentFunding);
            map.put("targetAmount", target);
            map.put("fundingPercentage", target > 0 ? (currentFunding / target) * 100.0 : 0.0);
            map.put("isFullyFunded", currentFunding >= target && target > 0);
            return map;
        }).toList());

        return response;
    }

    // --- INVESTOR PORTFOLIO SECTION ---

    @GetMapping("/investor/me/portfolio")
    @PreAuthorize("hasAuthority('INVESTOR')")
    public Map<String, Object> getMyInvestorPortfolio(Authentication authentication) {
        User investor = userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        return buildInvestorPortfolioResponse(investor.getId());
    }

    @GetMapping("/investor/{investorId}/portfolio")
    @PreAuthorize("hasAuthority('INVESTOR')")
    public Map<String, Object> getInvestorPortfolio(@PathVariable Long investorId, Authentication authentication) {
        User investor = userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        return buildInvestorPortfolioResponse(investor.getId());
    }

    private Map<String, Object> buildInvestorPortfolioResponse(Long investorId) {
        List<Investment> investments = projectService.getInvestorPortfolio(investorId);

        User investor = investments.stream()
                .map(Investment::getInvestor)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> {
                    User emptyInvestor = new User();
                    emptyInvestor.setWalletBalance(java.math.BigDecimal.ZERO);
                    return emptyInvestor;
                });

        double capitalDeployed = investments.stream()
                .filter(inv -> "COMPLETED".equals(inv.getStatus()))
                .mapToDouble(Investment::getAmountInvested)
                .sum();

        double currentHoldings = investments.stream()
                .filter(inv -> "COMPLETED".equals(inv.getStatus()))
                .mapToDouble(inv -> inv.isSettled() && inv.getFinalReturn() != null
                        ? inv.getFinalReturn()
                        : inv.getAmountInvested())
                .sum();

        Map<String, String> cropAllocation = investments.stream()
                .collect(Collectors.groupingBy(
                        inv -> inv.getProject().getCropType(),
                        Collectors.summingDouble(Investment::getAmountInvested)
                )).entrySet().stream().collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> capitalDeployed > 0
                                ? String.format("%.1f%%", (e.getValue() / capitalDeployed) * 100)
                                : "0.0%"
                ));

        Map<String, Object> response = new HashMap<>();
        response.put("summary", Map.of(
                "capitalDeployed", capitalDeployed,
                "currentHoldings", currentHoldings,
                "walletBalance", investor.getWalletBalance() != null ? investor.getWalletBalance() : 0.0,
                "totalPortfolioValue", currentHoldings,
                "activeInvestmentsCount", investments.stream().filter(inv -> "COMPLETED".equals(inv.getStatus())).count(),
                "impactFarmersHelped", investments.stream().map(inv -> inv.getProject().getFarmer().getId()).distinct().count()
        ));
        response.put("riskProfile", Map.of("cropDistribution", cropAllocation));
        response.put("investments", investments.stream().map(inv -> {
            Map<String, Object> investment = new HashMap<>();
            java.math.BigDecimal projectFunding = inv.getProject().getCurrentFunding();
            double currentFunding = (projectFunding != null && projectFunding.compareTo(java.math.BigDecimal.ZERO) > 0)
                    ? projectFunding.doubleValue()
                    : projectService.getAmountRaised(inv.getProject().getId());
            double target = inv.getProject().getTargetAmount() != null ? inv.getProject().getTargetAmount().doubleValue() : 0.0;
            investment.put("projectId", inv.getProject().getId());
            investment.put("crop", inv.getProject().getCropType());
            investment.put("amountInvested", inv.getAmountInvested());
            investment.put("status", inv.getProject().getStatus());
            investment.put("paymentStatus", inv.getStatus());
            investment.put("currentFunding", currentFunding);
            investment.put("targetAmount", target);
            investment.put("fundingPercentage", target > 0 ? (currentFunding / target) * 100.0 : 0.0);
            investment.put("settled", inv.isSettled());
            investment.put("finalReturn", inv.getFinalReturn());
            return investment;
        }).toList());

        return response;
    }

    @GetMapping("/investor/investment/{id}/receipt")
    public Map<String, Object> getInvestmentReceipt(@PathVariable Long id) {
        Investment inv = investmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Investment not found"));

        if (!"COMPLETED".equals(inv.getStatus())) {
            throw new IllegalStateException("Receipt only available for completed payments.");
        }

        return Map.of(
                "certificateNo", "AGRI-" + inv.getId() + "-" + inv.getInvestedAt().getYear(),
                "investorName", inv.getInvestor().getFullName(),
                "projectTitle", inv.getProject().getTitle(),
                "amount", inv.getAmountInvested(),
                "date", inv.getInvestedAt()
        );
    }

    // --- VILLAGE LEAD SECTION (The Gatekeeper) ---

    @GetMapping("/lead/portfolio")
    public Map<String, Object> getLeadPortfolio(@RequestParam(required = false) String region) {
        List<Milestone> pendingMilestones = (region != null && !region.isEmpty())
                ? milestoneRepository.findByStatusAndFarmProject_LocationContainingIgnoreCase("SUBMITTED", region)
                : milestoneRepository.findByStatus("SUBMITTED");

        List<Map<String, Object>> taskQueue = pendingMilestones.stream().map(m -> {
            Map<String, Object> task = new HashMap<>();
            task.put("milestoneId", m.getId());
            task.put("projectTitle", m.getFarmProject().getTitle());
            task.put("location", m.getFarmProject().getLocation());
            task.put("requestedAmount", m.getAmountToRelease());
            task.put("proofOfWork", m.getProofImageUrl());
            return task;
        }).toList();

        return Map.of(
                "regionFiltered", region != null ? region : "All Regions",
                "pendingVerificationsCount", pendingMilestones.size(),
                "verificationQueue", taskQueue
        );
    }

    /**
     * The Critical "Verification" Action
     * This moves money from Escrow to the Farmer's withdrawable balance.
     */
    @PostMapping("/lead/verify-milestone/{milestoneId}")
    @PreAuthorize("hasAuthority('VILLAGE_LEAD')")
    @Transactional
    public ResponseEntity<Map<String, Object>> verifyMilestone(
            @PathVariable Long milestoneId,
            @RequestParam boolean approved,
            Authentication authentication) {
        Milestone milestone = approved
                ? milestoneService.verifyAndRelease(milestoneId, authentication.getName())
                : milestoneService.rejectMilestone(milestoneId);

        return ResponseEntity.ok(Map.of(
                "milestoneId", milestoneId,
                "status", milestone.getStatus(),
                "amountReleased", milestone.getAmountToRelease(),
                "message", approved ? "Funds authorized for farmer withdrawal." : "Verification rejected."
        ));
    }
}
