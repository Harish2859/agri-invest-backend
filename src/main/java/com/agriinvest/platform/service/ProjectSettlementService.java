package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Investment;
import com.agriinvest.platform.entity.Milestone;
import com.agriinvest.platform.entity.ProjectStatus;
import com.agriinvest.platform.repository.InvestmentRepository;
import com.agriinvest.platform.repository.MilestoneRepository;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ProjectSettlementService {

    private final ProjectRepository projectRepository;
    private final InvestmentRepository investmentRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;

    @Autowired
    public ProjectSettlementService(ProjectRepository projectRepository,
                                    InvestmentRepository investmentRepository,
                                    MilestoneRepository milestoneRepository,
                                    UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.investmentRepository = investmentRepository;
        this.milestoneRepository = milestoneRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FarmProject settleProject(Long projectId, Double finalRevenue) {
        if (finalRevenue == null || finalRevenue < 0) {
            throw new IllegalArgumentException("Final revenue must be zero or greater.");
        }

        FarmProject project = projectRepository.findByIdWithLock(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getStatus() == ProjectStatus.COMPLETED) {
            throw new IllegalStateException("Project is already settled.");
        }

        if (project.getStatus() != ProjectStatus.FULLY_FUNDED) {
            throw new IllegalStateException("Only fully funded projects can be settled.");
        }

        BigDecimal targetAmount = project.getTargetAmount() != null ? project.getTargetAmount() : BigDecimal.ZERO;
        if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Project target amount must be greater than zero for settlement.");
        }

        double equityOffered = project.getEquityOffered() != null ? project.getEquityOffered() : 0.0;
        if (equityOffered < 0 || equityOffered > 100) {
            throw new IllegalStateException("Project equity offered must be between 0 and 100.");
        }
        if (equityOffered == 0.0) {
            throw new IllegalStateException("Project equity offered is 0%; settlement is blocked to prevent zero investor payout.");
        }

        BigDecimal finalRevenueValue = BigDecimal.valueOf(finalRevenue);
        BigDecimal leadCommission = finalRevenueValue.multiply(BigDecimal.valueOf(0.05));
        BigDecimal remainingAfterLead = finalRevenueValue.subtract(leadCommission);
        BigDecimal investorTotalPool = remainingAfterLead.multiply(BigDecimal.valueOf(equityOffered / 100.0));
        BigDecimal farmerShare = remainingAfterLead.subtract(investorTotalPool);

        // Credit lead governance commission to the latest verified lead for this project.
        if (leadCommission.compareTo(BigDecimal.ZERO) > 0) {
            Milestone lastVerifiedMilestone = milestoneRepository
                    .findTopByFarmProjectIdAndIsVerifiedTrueAndVerifiedByIsNotNullOrderByVerifiedAtDesc(projectId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot settle project: no verified lead found to receive commission."));

            BigDecimal leadWallet = lastVerifiedMilestone.getVerifiedBy().getWalletBalance() != null
                    ? lastVerifiedMilestone.getVerifiedBy().getWalletBalance()
                    : BigDecimal.ZERO;
            lastVerifiedMilestone.getVerifiedBy().setWalletBalance(leadWallet.add(leadCommission));
            userRepository.save(lastVerifiedMilestone.getVerifiedBy());
        }

        if (project.getFarmer() == null || project.getFarmer().getId() == null) {
            throw new IllegalStateException("Project is missing a farmer.");
        }

        BigDecimal milestonesAlreadyReleased = project.getReleasedToFarmer() != null
                ? project.getReleasedToFarmer()
                : BigDecimal.ZERO;
        BigDecimal netHarvestPayout = farmerShare.subtract(milestonesAlreadyReleased);
        if (netHarvestPayout.compareTo(BigDecimal.ZERO) < 0) {
            netHarvestPayout = BigDecimal.ZERO;
        }

        BigDecimal farmerCurrentWallet = project.getFarmer().getWalletBalance() != null
                ? project.getFarmer().getWalletBalance()
                : BigDecimal.ZERO;
        project.getFarmer().setWalletBalance(farmerCurrentWallet.add(netHarvestPayout));
        userRepository.save(project.getFarmer());

        List<Investment> investments = investmentRepository.findByProjectIdAndStatus(projectId, "COMPLETED");
        if (investments.isEmpty()) {
            throw new IllegalStateException("Cannot settle project: no completed investments found.");
        }

        for (Investment investment : investments) {
            if (investment.getAmountInvested() == null || investment.getAmountInvested() <= 0) {
                throw new IllegalStateException("Investment " + investment.getId() + " has invalid invested amount.");
            }
            BigDecimal sharePercentage = BigDecimal.valueOf(investment.getAmountInvested())
                    .divide(targetAmount, 8, RoundingMode.HALF_UP);
            BigDecimal finalPayout = sharePercentage.multiply(investorTotalPool);

            if (investment.getInvestor() == null || investment.getInvestor().getId() == null) {
                throw new IllegalStateException("Investment " + investment.getId() + " is missing an investor.");
            }

            BigDecimal currentWalletBalance = investment.getInvestor().getWalletBalance() != null
                    ? investment.getInvestor().getWalletBalance()
                    : BigDecimal.ZERO;
            investment.getInvestor().setWalletBalance(currentWalletBalance.add(finalPayout));
            userRepository.save(investment.getInvestor());

            investment.setFinalReturn(finalPayout.doubleValue());
            investment.setSettled(true);
            investmentRepository.save(investment);
        }

        project.setFinalFarmerProfit(netHarvestPayout);
        project.setStatus(ProjectStatus.COMPLETED);
        return projectRepository.save(project);
    }
}
