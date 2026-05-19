package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Investment;
import com.agriinvest.platform.entity.ProjectStatus;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.service.ProjectService;
import com.agriinvest.platform.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void getInvestorPortfolioSeparatesCapitalDeployedFromCurrentHoldings() {
        DashboardController controller = new DashboardController();

        User farmer = new User();
        farmer.setId(41L);

        User investor = new User();
        investor.setId(11L);
        investor.setWalletBalance(75.0);

        FarmProject activeProject = new FarmProject();
        activeProject.setId(1L);
        activeProject.setCropType("Rice");
        activeProject.setStatus(ProjectStatus.FUNDING_IN_PROGRESS);
        activeProject.setFarmer(farmer);
        activeProject.setCurrentFunding(100.0);
        activeProject.setTargetAmount(300.0);

        FarmProject completedProject = new FarmProject();
        completedProject.setId(2L);
        completedProject.setCropType("Wheat");
        completedProject.setStatus(ProjectStatus.COMPLETED);
        completedProject.setFarmer(farmer);
        completedProject.setCurrentFunding(200.0);
        completedProject.setTargetAmount(300.0);

        Investment activeInvestment = new Investment();
        activeInvestment.setInvestor(investor);
        activeInvestment.setProject(activeProject);
        activeInvestment.setAmountInvested(100.0);
        activeInvestment.setStatus("COMPLETED");
        activeInvestment.setSettled(false);

        Investment settledInvestment = new Investment();
        settledInvestment.setInvestor(investor);
        settledInvestment.setProject(completedProject);
        settledInvestment.setAmountInvested(200.0);
        settledInvestment.setStatus("COMPLETED");
        settledInvestment.setSettled(true);
        settledInvestment.setFinalReturn(260.0);

        ReflectionTestUtils.setField(controller, "projectService", new ProjectService() {
            @Override
            public List<Investment> getInvestorPortfolio(Long investorId) {
                return List.of(activeInvestment, settledInvestment);
            }
        });
        ReflectionTestUtils.setField(controller, "userService", new UserService() {
            @Override
            public Optional<User> findByEmail(String email) {
                return Optional.of(investor);
            }
        });

        Authentication authentication = new Authentication() {
            @Override
            public String getName() {
                return investor.getEmail();
            }

            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return List.of();
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
        };

        Map<String, Object> response = controller.getMyInvestorPortfolio(authentication);
        Map<String, Object> summary = (Map<String, Object>) response.get("summary");
        Map<String, Object> riskProfile = (Map<String, Object>) response.get("riskProfile");
        Map<String, String> cropDistribution = (Map<String, String>) riskProfile.get("cropDistribution");
        List<Map<String, Object>> investments = (List<Map<String, Object>>) response.get("investments");

        assertThat(summary.get("capitalDeployed")).isEqualTo(300.0);
        assertThat(summary.get("currentHoldings")).isEqualTo(360.0);
        assertThat(summary.get("walletBalance")).isEqualTo(75.0);
        assertThat(summary.get("totalPortfolioValue")).isEqualTo(360.0);
        assertThat(cropDistribution).containsEntry("Rice", "33.3%");
        assertThat(cropDistribution).containsEntry("Wheat", "66.7%");
        assertThat(investments).anySatisfy(investmentMap -> {
            assertThat(investmentMap.get("settled")).isEqualTo(true);
            assertThat(investmentMap.get("finalReturn")).isEqualTo(260.0);
        });
    }
}
