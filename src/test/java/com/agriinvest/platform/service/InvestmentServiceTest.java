package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Investment;
import com.agriinvest.platform.entity.ProjectStatus;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.repository.InvestmentRepository;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentServiceTest {

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MilestoneService milestoneService;

    @InjectMocks
    private InvestmentService investmentService;

    @Test
    void initiateInvestmentCountsOnlyCompletedFundsForOverfundingCheck() {
        FarmProject project = new FarmProject();
        project.setId(5L);
        project.setStatus(ProjectStatus.FUNDING_IN_PROGRESS);
        project.setTargetAmount(BigDecimal.valueOf(1000.0));

        Investment investment = new Investment();
        investment.setProject(project);
        investment.setAmountInvested(150.0);
        User investor = new User();
        investor.setId(99L);
        investment.setInvestor(investor);

        when(projectRepository.findById(5L)).thenReturn(Optional.of(project));
        when(investmentRepository.getActualRaisedAmount(5L)).thenReturn(900.0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> investmentService.initiateInvestment(investment));

        assertThat(exception.getMessage()).contains("Only");
        verify(investmentRepository).getActualRaisedAmount(5L);
    }

    @Test
    void completeInvestmentMarksProjectFullyFundedUsingCompletedMoneyOnly() {
        FarmProject project = new FarmProject();
        project.setId(7L);
        project.setTargetAmount(BigDecimal.valueOf(1000.0));
        project.setEscrowBalance(BigDecimal.valueOf(850.0));
        project.setCurrentFunding(BigDecimal.valueOf(850.0));
        project.setStatus(ProjectStatus.FUNDING_IN_PROGRESS);

        Investment investment = new Investment();
        investment.setId(12L);
        investment.setProject(project);
        User investor = new User();
        investor.setId(45L);
        investor.setWalletBalance(BigDecimal.valueOf(300.0));
        investment.setInvestor(investor);
        investment.setAmountInvested(150.0);
        investment.setStatus("PENDING");

        when(investmentRepository.findById(12L)).thenReturn(Optional.of(investment));
        when(projectRepository.findByIdWithLock(7L)).thenReturn(Optional.of(project));
        when(userRepository.findByIdWithLock(45L)).thenReturn(Optional.of(investor));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectRepository.save(any(FarmProject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(investmentRepository.save(any(Investment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Investment saved = investmentService.completeInvestment(12L, "txn-123");

        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(project.getEscrowBalance()).isEqualByComparingTo(BigDecimal.valueOf(1000.0));
        assertThat(investor.getWalletBalance()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.FULLY_FUNDED);
        verify(milestoneService).generateDefaultMilestones(project);
    }
}
