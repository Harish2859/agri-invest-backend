package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.Withdrawal;
import com.agriinvest.platform.repository.WithdrawalRepository;
import com.agriinvest.platform.service.WithdrawalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;
    private final WithdrawalRepository withdrawalRepository;

    @Autowired
    public WithdrawalController(WithdrawalService withdrawalService, WithdrawalRepository withdrawalRepository) {
        this.withdrawalService = withdrawalService;
        this.withdrawalRepository = withdrawalRepository;
    }

    /**
     * View Payout History for a project
     */
    @GetMapping("/project/{projectId}")
    public List<Map<String, Object>> getWithdrawalsByProject(@PathVariable Long projectId) {
        return withdrawalRepository.findByProjectId(projectId).stream()
                .map(this::toWithdrawalResponse)
                .toList();
    }

    @GetMapping("/my-history")
    @PreAuthorize("hasAnyAuthority('FARMER','VILLAGE_LEAD')")
    public ResponseEntity<List<Map<String, Object>>> getMyHistory(Authentication authentication) {
        return ResponseEntity.ok(withdrawalService.getMyHistory(authentication.getName()).stream()
                .map(this::toWithdrawalResponse)
                .toList());
    }

    /**
     * The "Cash Out" Action
     */
    @PostMapping("/request")
    @PreAuthorize("hasAuthority('FARMER')")
    public ResponseEntity<?> requestCashOut(@RequestBody Map<String, Object> request, Authentication authentication) {
        try {
            // 1. Safety extraction for Project ID (Handles "projectId" or nested "project": {"id": 1})
            Long projectId;
            if (request.containsKey("projectId")) {
                projectId = Long.valueOf(request.get("projectId").toString());
            } else if (request.get("project") instanceof Map) {
                Map<?, ?> projectMap = (Map<?, ?>) request.get("project");
                projectId = Long.valueOf(projectMap.get("id").toString());
            } else {
                throw new RuntimeException("Missing project information");
            }

            // 2. Extract Amount and Bank Details with null checks
            if (request.get("amount") == null || request.get("bankDetails") == null) {
                throw new RuntimeException("Amount and Bank Details are mandatory");
            }

            Double amount = Double.valueOf(request.get("amount").toString());
            String bankDetails = request.get("bankDetails").toString();

            // 3. Process the withdrawal
            Withdrawal withdrawal = withdrawalService.requestWithdrawal(
                    projectId,
                    amount,
                    bankDetails,
                    authentication.getName()
            );
            return ResponseEntity.ok(toWithdrawalResponse(withdrawal));

        } catch (Exception e) {
            // This will now return a clean error message instead of a crash
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toWithdrawalResponse(Withdrawal withdrawal) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", withdrawal.getId());
        response.put("amount", withdrawal.getAmount());
        response.put("status", withdrawal.getStatus());
        response.put("requestedAt", withdrawal.getRequestedAt());
        response.put("bankDetails", withdrawal.getBankDetails());
        response.put("bankAccountNumber", withdrawal.getBankAccountNumber());
        response.put("projectId", withdrawal.getProject() != null ? withdrawal.getProject().getId() : null);
        response.put("userId", withdrawal.getUser() != null ? withdrawal.getUser().getId() : null);
        return response;
    }
}
