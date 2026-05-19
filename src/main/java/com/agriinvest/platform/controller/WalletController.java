package com.agriinvest.platform.controller;

import com.agriinvest.platform.dto.WithdrawRequest;
import com.agriinvest.platform.entity.Withdrawal;
import com.agriinvest.platform.service.WithdrawalService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WithdrawalService withdrawalService;

    public WalletController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasAnyAuthority('FARMER','VILLAGE_LEAD')")
    public ResponseEntity<?> withdraw(@RequestBody WithdrawRequest request, Authentication authentication) {
        try {
            Withdrawal withdrawal = withdrawalService.requestWalletWithdrawal(
                    request.getAmount(),
                    request.getBankDetails(),
                    authentication.getName());
            return ResponseEntity.ok(Map.of(
                    "message", "Withdrawal processed successfully to " + request.getBankDetails(),
                    "withdrawalId", withdrawal.getId(),
                    "status", withdrawal.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
