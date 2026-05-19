package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.Notification;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.repository.NotificationRepository;
import com.agriinvest.platform.repository.UserRepository;
import com.agriinvest.platform.service.MilestoneService;
import com.agriinvest.platform.service.NotificationService;
import com.agriinvest.platform.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;
    private ProjectService projectService;
    private MilestoneService milestoneService;
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
        projectService = new ProjectService();
        milestoneService = new MilestoneService();
        adminController = new AdminController(userRepository, notificationService, projectService, milestoneService);
    }

    @Test
    void verifyFarmerApprovesFarmer() {
        User farmer = new User();
        farmer.setId(11L);
        farmer.setFullName("Sita Farmer");
        farmer.setEmail("sita@example.com");
        farmer.setRole(User.Role.FARMER);

        when(userRepository.findById(11L)).thenReturn(Optional.of(farmer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = adminController.verifyFarmer(11L, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "Farmer Sita Farmer is now VERIFIED."));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isVerified()).isTrue();

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getRecipientEmail()).isEqualTo("sita@example.com");
    }

    @Test
    void verifyFarmerRejectsFarmer() {
        User farmer = new User();
        farmer.setId(12L);
        farmer.setFullName("Mohan Farmer");
        farmer.setEmail("mohan@example.com");
        farmer.setRole(User.Role.FARMER);
        farmer.setVerified(true);

        when(userRepository.findById(12L)).thenReturn(Optional.of(farmer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = adminController.verifyFarmer(12L, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "Farmer verification rejected."));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isVerified()).isFalse();

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getRecipientEmail()).isEqualTo("mohan@example.com");
    }

    @Test
    void verifyFarmerRejectsNonFarmerAccounts() {
        User investor = new User();
        investor.setId(13L);
        investor.setFullName("Investor User");
        investor.setRole(User.Role.INVESTOR);

        when(userRepository.findById(13L)).thenReturn(Optional.of(investor));

        ResponseEntity<?> response = adminController.verifyFarmer(13L, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "Only farmers can be verified"));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void verifyFarmerFailsWhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> adminController.verifyFarmer(99L, true));

        assertThat(exception.getMessage()).isEqualTo("Farmer not found");
    }

    @Test
    void verifyFarmerEndpointRequiresVillageLeadAuthorityAnnotation() throws NoSuchMethodException {
        PreAuthorize annotation = AdminController.class
                .getMethod("verifyFarmer", Long.class, boolean.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('VILLAGE_LEAD')");
    }

    @Test
    void getPendingProjectsReturnsPendingProjectsFromService() {
        FarmProject project = new FarmProject();
        project.setId(77L);

        ProjectService pendingProjectService = new ProjectService() {
            @Override
            public List<FarmProject> getProjectsByStatus(String status) {
                return List.of(project);
            }
        };
        adminController = new AdminController(userRepository, notificationService, pendingProjectService, milestoneService);

        ResponseEntity<List<FarmProject>> response = adminController.getPendingProjects();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getId()).isEqualTo(77L);
    }
}
