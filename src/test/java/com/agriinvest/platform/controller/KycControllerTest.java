package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycControllerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KycController kycController;

    @Test
    void uploadKycSavesDocumentUrlAndResetsVerification() {
        User farmer = new User();
        farmer.setEmail("farmer@example.com");
        farmer.setRole(User.Role.FARMER);
        farmer.setVerified(true);

        when(userRepository.findByEmail("farmer@example.com")).thenReturn(Optional.of(farmer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = kycController.uploadKyc(
                Map.of("documentUrl", "https://files.example/kyc.pdf"),
                new TestingAuthenticationToken("farmer@example.com", null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "KYC document submitted successfully for review."));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getKycDocumentUrl()).isEqualTo("https://files.example/kyc.pdf");
        assertThat(userCaptor.getValue().isVerified()).isFalse();
    }

    @Test
    void uploadKycRejectsBlankDocumentUrl() {
        ResponseEntity<?> response = kycController.uploadKyc(
                Map.of("documentUrl", " "),
                new TestingAuthenticationToken("farmer@example.com", null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(Map.of("error", "documentUrl is required"));
    }

    @Test
    void uploadKycFailsWhenAuthenticatedUserIsMissing() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class, () -> kycController.uploadKyc(
                Map.of("documentUrl", "https://files.example/kyc.pdf"),
                new TestingAuthenticationToken("missing@example.com", null)
        ));

        assertThat(exception.getMessage()).isEqualTo("User not found");
    }

    @Test
    void uploadKycEndpointRequiresFarmerRoleAnnotation() throws NoSuchMethodException {
        PreAuthorize annotation = KycController.class
                .getMethod("uploadKyc", Map.class, org.springframework.security.core.Authentication.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('FARMER')");
    }
}
