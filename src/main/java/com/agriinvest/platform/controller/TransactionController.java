package com.agriinvest.platform.controller;

import com.agriinvest.platform.entity.TransactionRecord;
import com.agriinvest.platform.repository.TransactionRecordRepository;
import com.agriinvest.platform.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRecordRepository transactionRepository;
    private final UserRepository userRepository;

    public TransactionController(TransactionRecordRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyLedger(Principal principal) {
        return userRepository.findByEmail(principal.getName())
            .map(user -> {
                List<TransactionRecord> records = transactionRepository.findByUserIdOrderByLocalizedTimestampDesc(user.getId());
                
                List<Map<String, Object>> response = records.stream().map(txn -> Map.<String, Object>of(
                    "referenceId", txn.getReferenceId(),
                    "transactionType", txn.getTransactionType(),
                    "precisionAmount", txn.getPrecisionAmount().toString(),
                    "localizedTimestamp", txn.getLocalizedTimestamp().toString(),
                    "processingStatus", txn.getProcessingStatus()
                )).collect(Collectors.toList());
                
                return ResponseEntity.ok(response);
            })
            .orElse(ResponseEntity.status(401).build());
    }
}

