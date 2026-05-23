package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Investment;
import com.agriinvest.platform.entity.ProjectStatus;
import com.agriinvest.platform.repository.InvestmentRepository;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class InvestmentService {

    private final InvestmentRepository investmentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MilestoneService milestoneService;

    public InvestmentService(InvestmentRepository investmentRepository,
                             ProjectRepository projectRepository,
                             UserRepository userRepository,
                             NotificationService notificationService,
                             MilestoneService milestoneService) {
        this.investmentRepository = investmentRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.milestoneService = milestoneService;
    }

    /**
     * Phase 1: Initiation
     * Creates a PENDING investment record so we can track the transaction.
     */
    @Transactional
    public Investment initiateInvestment(Investment investment) {
        if (investment.getInvestor() == null || investment.getInvestor().getId() == null) {
            throw new IllegalArgumentException("Investor identity is required.");
        }
        if (investment.getProject() == null || investment.getProject().getId() == null) {
            throw new IllegalArgumentException("Project ID is required.");
        }
        if (investment.getAmountInvested() == null || investment.getAmountInvested() <= 0) {
            throw new IllegalArgumentException("Investment amount must be greater than zero.");
        }

        Long projectId = investment.getProject().getId();

        // Read-only lookup here to avoid holding a pessimistic lock during initiation.
        // The authoritative funding mutation happens in completeInvestment().
        FarmProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found with ID: " + projectId));

        // 2. Security Check
        if (project.getStatus() != ProjectStatus.FUNDING_IN_PROGRESS) {
            throw new IllegalStateException("Project is not accepting funds. Status: " + project.getStatus());
        }

        // 3. Over-funding Check (Pre-check)
        Double currentRaised = investmentRepository.getActualRaisedAmount(projectId);
        if (currentRaised == null) currentRaised = 0.0;

        Double remainingNeeded = project.getTargetAmount().doubleValue() - currentRaised;
        if (investment.getAmountInvested() > remainingNeeded) {
            throw new IllegalArgumentException("Only ₹" + remainingNeeded + " remains for this project.");
        }

        // 4. Set Initial Meta-data
        investment.setStatus("PENDING");
        investment.setInvestedAt(LocalDateTime.now());

        Investment savedInvestment = investmentRepository.save(investment);

        if (project.getFarmer() != null && project.getFarmer().getEmail() != null) {
            String investorName = investment.getInvestor() != null && investment.getInvestor().getFullName() != null
                    ? investment.getInvestor().getFullName()
                    : "An investor";
            notificationService.createNotification(
                    project.getFarmer().getEmail(),
                    investorName + " initiated an investment of Rs " + investment.getAmountInvested()
                            + " in project \"" + project.getTitle() + "\"."
            );
        }

        return savedInvestment;
    }

    /**
     * Phase 2: Completion
     * Marks the investment as COMPLETED once the payment gateway confirms success.
     */
    @Transactional
    public Investment completeInvestment(Long investmentId, String transactionId) {
        Investment inv = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new RuntimeException("Investment record not found"));

        if ("COMPLETED".equals(inv.getStatus())) {
            return inv; // Already processed
        }

        FarmProject project = projectRepository.findByIdWithLock(inv.getProject().getId())
                .orElseThrow(() -> new RuntimeException("Project not found with ID: " + inv.getProject().getId()));
        if (inv.getInvestor() == null || inv.getInvestor().getId() == null) {
            throw new RuntimeException("Investor not found for this investment");
        }

        var investor = userRepository.findByIdWithLock(inv.getInvestor().getId())
                .orElseThrow(() -> new RuntimeException("Investor profile not found"));

        BigDecimal amountInvested = BigDecimal.valueOf(inv.getAmountInvested());
        if (amountInvested.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Investment amount must be greater than zero.");
        }

        BigDecimal walletBalance = investor.getWalletBalance() != null ? investor.getWalletBalance() : BigDecimal.ZERO;
        if (walletBalance.compareTo(amountInvested) < 0) {
            throw new IllegalArgumentException("Insufficient wallet balance. Please add funds before investing.");
        }

        investor.setWalletBalance(walletBalance.subtract(amountInvested));

        inv.setStatus("COMPLETED");
        inv.setTransactionId(transactionId);
        inv.setInvestmentDate(LocalDateTime.now());
        Investment savedInvestment = investmentRepository.save(inv);

        BigDecimal currentFunding = project.getCurrentFunding() != null ? project.getCurrentFunding() : BigDecimal.ZERO;
        BigDecimal escrowBalance = project.getEscrowBalance() != null ? project.getEscrowBalance() : BigDecimal.ZERO;
        project.setCurrentFunding(currentFunding.add(amountInvested));
        project.setEscrowBalance(escrowBalance.add(amountInvested));

        if (project.getCurrentFunding().compareTo(project.getTargetAmount()) >= 0) {
            project.setStatus(ProjectStatus.FULLY_FUNDED);
            milestoneService.generateDefaultMilestones(project);
        }

        userRepository.save(investor);
        projectRepository.save(project);
        return savedInvestment;
    }

    @Transactional(readOnly = true)
    public String getInvestorEmail(Long investmentId) {
        Investment inv = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new RuntimeException("Investment record not found"));
        if (inv.getInvestor() == null || inv.getInvestor().getEmail() == null) {
            throw new RuntimeException("Investor not found for this investment");
        }
        return inv.getInvestor().getEmail();
    }

    @Transactional
    public Investment processSecureCompletion(Long investmentId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required.");
        }

        Investment inv = investmentRepository.findById(investmentId)
                .orElseThrow(() -> new RuntimeException("Investment record not found"));

        if ("COMPLETED".equals(inv.getStatus())) {
            if (inv.getTransactionId() != null && !inv.getTransactionId().equals(idempotencyKey)) {
                throw new IllegalStateException("Investment already completed with a different idempotency key.");
            }
            return inv;
        }

        return completeInvestment(investmentId, idempotencyKey);
    }
}
