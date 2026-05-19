package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.repository.InvestmentRepository;
import com.agriinvest.platform.repository.MilestoneRepository;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.WithdrawalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private InvestmentRepository investmentRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private WithdrawalRepository withdrawalRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void getAmountRaisedUsesCompletedInvestmentTotal() {
        when(investmentRepository.getActualRaisedAmount(3L)).thenReturn(4200.0);

        assertThat(projectService.getAmountRaised(3L)).isEqualTo(4200.0);
    }

    @Test
    void getAmountRaisedFallsBackToZeroWhenRepositoryReturnsNull() {
        when(investmentRepository.getActualRaisedAmount(4L)).thenReturn(null);

        assertThat(projectService.getAmountRaised(4L)).isEqualTo(0.0);
    }
}
