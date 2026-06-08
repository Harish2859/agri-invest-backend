package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.entity.TransactionRecord;
import com.agriinvest.platform.repository.TransactionRecordRepository;
import com.agriinvest.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class WalletService {

    private final UserRepository userRepository;
    private final TransactionRecordRepository transactionRepository;

    public WalletService(UserRepository userRepository, TransactionRecordRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public User addFunds(String email, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero");
        }

        User user = userRepository.findByEmailWithLock(email)
                .orElseThrow(() -> new RuntimeException("User profile not found"));

        BigDecimal currentBalance = user.getWalletBalance();
        if (currentBalance == null) {
            currentBalance = BigDecimal.ZERO;
        }

        user.setWalletBalance(currentBalance.add(amount));
        User savedUser = userRepository.save(user);
        String refId = "TXN-DEP-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        transactionRepository.save(new TransactionRecord(refId, "DEPOSIT", amount, savedUser));
        return savedUser;
    }
}

