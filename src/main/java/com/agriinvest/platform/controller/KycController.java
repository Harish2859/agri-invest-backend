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

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class KycController {

    private final UserRepository userRepository;

    public KycController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/upload-kyc")
    @PreAuthorize("hasAuthority('FARMER')")
    public ResponseEntity<?> uploadKyc(@RequestBody Map<String, String> request, Authentication authentication) {
        String documentUrl = request.get("documentUrl");
        if (documentUrl == null || documentUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "documentUrl is required"));
        }

        User farmer = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        farmer.setKycDocumentUrl(documentUrl.trim());
        farmer.setKycStatus(KycStatus.SUBMITTED);
        farmer.setKycRejectionReason(null);
        farmer.setKycVerifiedAt(null);
        farmer.setVerified(false);
        userRepository.save(farmer);

        return ResponseEntity.ok(Map.of("message", "KYC document submitted successfully for review."));
    }
}
