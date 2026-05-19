package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.Investment;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.repository.InvestmentRepository;
import com.agriinvest.platform.repository.UserRepository;
import com.agriinvest.platform.service.InvestmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    @Autowired
    private InvestmentService investmentService;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private UserRepository userRepository;

    // 1. The "Pay" Action: Investor puts money into a farm
// 1. The "Pay" Action: Investor puts money into a farm
    @PostMapping("/pay")
    @PreAuthorize("hasAuthority('INVESTOR')")
    public ResponseEntity<?> createInvestment(@RequestBody Investment investment, Authentication authentication) {
        try {
            User investor = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Investor not found"));
            investment.setInvestor(investor);
            Investment saved = investmentService.initiateInvestment(investment);

            // Return a custom Map instead of the 'saved' object
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "amount", saved.getAmountInvested(),
                    "status", saved.getStatus(),
                    "message", "Investment initiated successfully!"
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(Map.of("error", "Investment initiation failed: " + e.getMessage()));
        }
    }

    // 2. The Investor Portfolio: See all my active investments
    @GetMapping("/portfolio/{investorId}")
    @PreAuthorize("hasAuthority('INVESTOR')")
    public ResponseEntity<List<Investment>> getInvestorPortfolio(@PathVariable Long investorId, Authentication authentication) {
        User investor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        return ResponseEntity.ok(investmentRepository.findByInvestorId(investor.getId()));
    }

    @GetMapping("/my-portfolio")
    @PreAuthorize("hasAuthority('INVESTOR')")
    public ResponseEntity<List<Investment>> getMyPortfolio(Authentication authentication) {
        User investor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        return ResponseEntity.ok(investmentRepository.findByInvestorId(investor.getId()));
    }

    @GetMapping("/my-history")
    @PreAuthorize("hasAuthority('INVESTOR')")
    public ResponseEntity<List<Investment>> getMyInvestmentHistory(Authentication authentication) {
        User investor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        return ResponseEntity.ok(investmentRepository.findByInvestorId(investor.getId()));
    }

    @GetMapping("/portfolio")
    @PreAuthorize("hasAuthority('INVESTOR')")
    public ResponseEntity<List<Investment>> getMyInvestorPortfolio(Authentication authentication) {
        User investor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        return ResponseEntity.ok(investmentRepository.findByInvestorId(investor.getId()));
    }

    @PostMapping("/complete/{id}")
    public ResponseEntity<?> complete(@PathVariable Long id, @RequestParam String txnId) {
        try {
            return ResponseEntity.ok(investmentService.completeInvestment(id, txnId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // 3. Project Specific: See who invested in a specific farm
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Investment>> getProjectInvestors(@PathVariable Long projectId) {
        return ResponseEntity.ok(investmentRepository.findByProjectId(projectId));
    }
}
