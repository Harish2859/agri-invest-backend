package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Investment;
import com.agriinvest.platform.entity.Milestone;
import com.agriinvest.platform.entity.ProjectStatus;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.repository.InvestmentRepository;
import com.agriinvest.platform.repository.MilestoneRepository;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSettlementServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectSettlementService projectSettlementService;

    @Test
    void settleProjectCreditsFarmerAndInvestorsUsingEquityPool() {
        User investor = new User();
        investor.setId(14L);
        investor.setWalletBalance(100.0);

        User farmer = new User();
        farmer.setId(77L);
        farmer.setWalletBalance(200.0);

        User lead = new User();
        lead.setId(91L);
        lead.setWalletBalance(25.0);

        FarmProject project = new FarmProject();
        project.setId(6L);
        project.setStatus(ProjectStatus.FULLY_FUNDED);
        project.setTargetAmount(1000.0);
        project.setEquityOffered(40.0);
        project.setFarmer(farmer);

        Milestone milestone = new Milestone();
        milestone.setId(501L);
        milestone.setFarmProject(project);
        milestone.setVerified(true);
        milestone.setVerifiedBy(lead);

        Investment investment = new Investment();
        investment.setId(21L);
        investment.setInvestor(investor);
        investment.setProject(project);
        investment.setAmountInvested(250.0);
        investment.setStatus("COMPLETED");

        when(projectRepository.findByIdWithLock(6L)).thenReturn(Optional.of(project));
        when(milestoneRepository.findTopByFarmProjectIdAndIsVerifiedTrueAndVerifiedByIsNotNullOrderByVerifiedAtDesc(6L))
                .thenReturn(Optional.of(milestone));
        when(investmentRepository.findByProjectIdAndStatus(6L, "COMPLETED")).thenReturn(List.of(investment));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.save(any(FarmProject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FarmProject settledProject = projectSettlementService.settleProject(6L, 2000.0);

        assertThat(investment.getFinalReturn()).isCloseTo(190.0, within(0.000001));
        assertThat(investment.isSettled()).isTrue();
        assertThat(investor.getWalletBalance()).isCloseTo(290.0, within(0.000001));
        assertThat(farmer.getWalletBalance()).isCloseTo(1340.0, within(0.000001));
        assertThat(lead.getWalletBalance()).isCloseTo(125.0, within(0.000001));
        assertThat(settledProject.getFinalFarmerProfit()).isCloseTo(1140.0, within(0.000001));
        assertThat(settledProject.getStatus()).isEqualTo(ProjectStatus.COMPLETED);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(userCaptor.capture());
        assertThat(userCaptor.getAllValues().stream().anyMatch(u -> u.getId().equals(77L))).isTrue();
        assertThat(userCaptor.getAllValues().stream().anyMatch(u -> u.getId().equals(14L))).isTrue();
        assertThat(userCaptor.getAllValues().stream().anyMatch(u -> u.getId().equals(91L))).isTrue();
    }

    @Test
    void settleProjectRejectsAlreadyCompletedProjects() {
        FarmProject project = new FarmProject();
        project.setId(9L);
        project.setStatus(ProjectStatus.COMPLETED);
        project.setTargetAmount(1000.0);

        when(projectRepository.findByIdWithLock(9L)).thenReturn(Optional.of(project));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> projectSettlementService.settleProject(9L, 1200.0));

        assertThat(exception.getMessage()).isEqualTo("Project is already settled.");
        verify(investmentRepository, times(0)).findByProjectIdAndStatus(any(), any());
    }

    @Test
    void settleProjectRejectsNonFullyFundedProjects() {
        FarmProject project = new FarmProject();
        project.setId(12L);
        project.setStatus(ProjectStatus.FUNDING_IN_PROGRESS);
        project.setTargetAmount(1000.0);

        when(projectRepository.findByIdWithLock(12L)).thenReturn(Optional.of(project));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> projectSettlementService.settleProject(12L, 1800.0));

        assertThat(exception.getMessage()).isEqualTo("Only fully funded projects can be settled.");
        verify(investmentRepository, times(0)).findByProjectIdAndStatus(any(), any());
    }

    @Test
    void settleProjectRejectsWhenNoCompletedInvestmentsFound() {
        User farmer = new User();
        farmer.setId(77L);
        farmer.setWalletBalance(200.0);

        FarmProject project = new FarmProject();
        project.setId(15L);
        project.setStatus(ProjectStatus.FULLY_FUNDED);
        project.setTargetAmount(1000.0);
        project.setEquityOffered(40.0);
        project.setFarmer(farmer);

        Milestone milestone = new Milestone();
        milestone.setId(601L);
        milestone.setFarmProject(project);
        milestone.setVerified(true);
        milestone.setVerifiedBy(new User());

        when(projectRepository.findByIdWithLock(15L)).thenReturn(Optional.of(project));
        when(milestoneRepository.findTopByFarmProjectIdAndIsVerifiedTrueAndVerifiedByIsNotNullOrderByVerifiedAtDesc(15L))
                .thenReturn(Optional.of(milestone));
        when(investmentRepository.findByProjectIdAndStatus(15L, "COMPLETED")).thenReturn(List.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> projectSettlementService.settleProject(15L, 1800.0));

        assertThat(exception.getMessage()).isEqualTo("Cannot settle project: no completed investments found.");
    }
}
