package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.Investment;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.repository.InvestmentRepository;
import com.agriinvest.platform.repository.UserRepository;
import com.agriinvest.platform.service.InvestmentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvestmentControllerTest {

    private static Authentication authentication(String email, User principal) {
        return new Authentication() {
            @Override
            public String getName() {
                return email;
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getDetails() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return principal;
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }

            @Override
            public void setAuthenticated(boolean isAuthenticated) {
            }

            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return List.of();
            }
        };
    }

    @Test
    void createInvestmentReturnsBadRequestForClosedProject() {
        InvestmentController controller = new InvestmentController();
        UserRepository userRepository = mock(UserRepository.class);
        User investor = new User();
        investor.setId(11L);
        investor.setEmail("investor@example.com");
        when(userRepository.findByEmail("investor@example.com")).thenReturn(Optional.of(investor));

        ReflectionTestUtils.setField(controller, "investmentService", new InvestmentService(null, null, null, null, null, null) {
            @Override
            public Investment initiateInvestment(Investment investment) {
                throw new IllegalStateException("Project is not accepting funds. Status: COMPLETED");
            }
        });
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);

        ResponseEntity<?> response = controller.createInvestment(new Investment(), authentication("investor@example.com", investor));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "error", "Project is not accepting funds. Status: COMPLETED"
        ));
    }

    @Test
    void completeInvestmentReturnsBadRequestForBusinessRuleErrors() {
        InvestmentController controller = new InvestmentController();
        ReflectionTestUtils.setField(controller, "investmentService", new InvestmentService(null, null, null, null, null, null) {
            @Override
            public Investment processSecureCompletion(Long id, String idempotencyKey) {
                throw new IllegalStateException("Investment already finalized");
            }
        });

        ResponseEntity<?> response = controller.complete(5L, Map.of("idempotency_key", "txn-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of(
                "error", "Investment already finalized"
        ));
    }

    @Test
    void investorPortfolioUsesAuthenticatedInvestorInsteadOfPathId() {
        InvestmentController controller = new InvestmentController();
        UserRepository userRepository = mock(UserRepository.class);
        InvestmentRepository investmentRepository = mock(InvestmentRepository.class);

        User investor = new User();
        investor.setId(11L);
        investor.setEmail("investor@example.com");

        Investment investment = new Investment();
        investment.setAmountInvested(500.0);

        when(userRepository.findByEmail("investor@example.com")).thenReturn(Optional.of(investor));
        when(investmentRepository.findByInvestorId(11L)).thenReturn(List.of(investment));

        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "investmentRepository", investmentRepository);

        Authentication authentication = new Authentication() {
            @Override
            public String getName() {
                return "investor@example.com";
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getDetails() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return investor;
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }

            @Override
            public void setAuthenticated(boolean isAuthenticated) {
            }

            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return List.of();
            }
        };

        ResponseEntity<List<Investment>> response = controller.getInvestorPortfolio(8L, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(investment);
    }
}

