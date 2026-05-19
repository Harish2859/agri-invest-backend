package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.Investment;
import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Milestone;
import com.agriinvest.platform.entity.ProjectStatus;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.entity.Withdrawal;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.InvestmentRepository;
import com.agriinvest.platform.repository.MilestoneRepository;
import com.agriinvest.platform.repository.UserRepository;
import com.agriinvest.platform.repository.WithdrawalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private UserRepository userRepository;

    public FarmProject createProject(FarmProject project) {
        if (project.getEquityOffered() == null) {
            throw new IllegalArgumentException("Equity offered is required.");
        }
        if (project.getEquityOffered() <= 0 || project.getEquityOffered() > 100) {
            throw new IllegalArgumentException("Equity offered must be greater than 0 and at most 100.");
        }
        project.setStatus(ProjectStatus.PENDING);
        return projectRepository.save(project);
    }

    public List<FarmProject> getAllProjects() {
        return projectRepository.findAll();
    }

    public Double getAmountRaised(Long projectId) {
        Double aggregatedFunding = investmentRepository.getActualRaisedAmount(projectId);
        return (aggregatedFunding != null) ? aggregatedFunding : 0.0;
    }

    public Double getWithdrawableAmount(Long projectId) {
        FarmProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        return project.getWithdrawableBalance() != null ? project.getWithdrawableBalance().doubleValue() : 0.0;
    }

    public List<FarmProject> getProjectsByCurrentFarmer(String email) {
        User farmer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Farmer not found"));
        return projectRepository.findByFarmerId(farmer.getId());
    }

    public Optional<FarmProject> findById(Long id) {
        return projectRepository.findById(id);
    }

    public List<FarmProject> getProjectsByStatus(String status) {
        return projectRepository.findByStatus(ProjectStatus.valueOf(status.toUpperCase()));
    }

    public FarmProject approveProject(Long projectId) {
        FarmProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        project.setStatus(ProjectStatus.FUNDING_IN_PROGRESS);
        return projectRepository.save(project);
    }

    public List<Investment> getInvestorPortfolio(Long investorId) {
        return investmentRepository.findByInvestorId(investorId);
    }

    @Transactional
    public FarmProject reconcileProjectFundingState(Long projectId, MilestoneService milestoneService) {
        FarmProject project = projectRepository.findByIdWithLock(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        Double reconciledFunding = investmentRepository.getActualRaisedAmount(projectId);
        double canonicalFunding = reconciledFunding != null ? reconciledFunding : 0.0;
        project.setCurrentFunding(BigDecimal.valueOf(canonicalFunding));
        project.setEscrowBalance(BigDecimal.valueOf(canonicalFunding));

        if (canonicalFunding >= project.getTargetAmount().doubleValue()) {
            project.setStatus(ProjectStatus.FULLY_FUNDED);
            milestoneService.generateDefaultMilestones(project);
        }

        return projectRepository.save(project);
    }
}
