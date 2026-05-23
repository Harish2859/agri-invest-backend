package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.entity.KycStatus;
import com.agriinvest.platform.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class KycController {

    private final UserRepository userRepository;

    public KycController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/upload-kyc")
    @PreAuthorize("hasAnyAuthority('FARMER','INVESTOR')")
    public ResponseEntity<?> uploadKyc(@RequestBody Map<String, String> request, Authentication authentication) {
        String documentUrl = request.get("documentUrl");
        if (documentUrl == null || documentUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "documentUrl is required"));
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setKycDocumentUrl(documentUrl.trim());
        user.setKycRejectionReason(null);
        if (user.getRole() == User.Role.INVESTOR) {
            user.setKycStatus(KycStatus.APPROVED);
            user.setKycVerifiedAt(LocalDateTime.now());
            user.setVerified(true);
        } else {
            user.setKycStatus(KycStatus.SUBMITTED);
            user.setKycVerifiedAt(null);
            user.setVerified(false);
        }
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", user.getRole() == User.Role.INVESTOR
                        ? "KYC submitted and auto-verified successfully."
                        : "KYC document submitted successfully for review.",
                "verified", user.isVerified(),
                "kycStatus", user.getKycStatus().name()
        ));
    }
}
