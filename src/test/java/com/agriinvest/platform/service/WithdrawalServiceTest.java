package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.entity.Withdrawal;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.UserRepository;
import com.agriinvest.platform.repository.WithdrawalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WithdrawalRepository withdrawalRepository;

    @InjectMocks
    private WithdrawalService withdrawalService;

    @Test
    void requestWithdrawalMovesFundsFromWithdrawableToReleased() {
        User farmer = new User();
        farmer.setId(9L);
        farmer.setEmail("farmer@example.com");

        FarmProject project = new FarmProject();
        project.setId(15L);
        project.setFarmer(farmer);
        project.setWithdrawableBalance(5000.0);
        project.setReleasedToFarmer(1200.0);

        when(userRepository.findByEmail("farmer@example.com")).thenReturn(Optional.of(farmer));
        when(projectRepository.findByIdWithLock(15L)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(FarmProject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(withdrawalRepository.save(any(Withdrawal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Withdrawal withdrawal = withdrawalService.requestWithdrawal(15L, 800.0, "AC 123", "farmer@example.com");

        assertThat(withdrawal.getStatus()).isEqualTo("COMPLETED");
        assertThat(project.getWithdrawableBalance()).isEqualTo(4200.0);
        assertThat(project.getReleasedToFarmer()).isEqualTo(2000.0);
    }

    @Test
    void requestWithdrawalRejectsAmountsAboveAuthorizedBalance() {
        User farmer = new User();
        farmer.setId(9L);
        farmer.setEmail("farmer@example.com");

        FarmProject project = new FarmProject();
        project.setId(15L);
        project.setFarmer(farmer);
        project.setWithdrawableBalance(500.0);
        project.setReleasedToFarmer(1200.0);

        when(userRepository.findByEmail("farmer@example.com")).thenReturn(Optional.of(farmer));
        when(projectRepository.findByIdWithLock(15L)).thenReturn(Optional.of(project));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> withdrawalService.requestWithdrawal(15L, 800.0, "AC 123", "farmer@example.com"));

        assertThat(exception.getMessage()).contains("Insufficient authorized funds");
    }
}
