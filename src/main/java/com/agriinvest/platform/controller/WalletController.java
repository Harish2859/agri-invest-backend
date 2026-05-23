package com.agriinvest.platform.controller;

import com.agriinvest.platform.dto.DepositRequest;
import com.agriinvest.platform.dto.WithdrawRequest;
import com.agriinvest.platform.entity.Withdrawal;
import com.agriinvest.platform.service.WalletService;
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
    private final WalletService walletService;

    public WalletController(WithdrawalService withdrawalService, WalletService walletService) {
        this.withdrawalService = withdrawalService;
        this.walletService = walletService;
    }

    @PostMapping("/deposit")
    @PreAuthorize("hasAuthority('INVESTOR')")
    public ResponseEntity<?> deposit(@RequestBody DepositRequest request, Authentication authentication) {
        try {
            var updatedUser = walletService.addFunds(authentication.getName(), request.getAmount());
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Funds credited successfully",
                    "newBalance", updatedUser.getWalletBalance()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
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
