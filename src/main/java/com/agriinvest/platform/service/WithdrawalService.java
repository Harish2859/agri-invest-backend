package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.entity.Withdrawal;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.UserRepository;
import com.agriinvest.platform.entity.TransactionRecord;
import com.agriinvest.platform.repository.TransactionRecordRepository;
import com.agriinvest.platform.repository.WithdrawalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WithdrawalService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final TransactionRecordRepository transactionRepository;

    @Autowired
    public WithdrawalService(ProjectRepository projectRepository,
                             UserRepository userRepository,
                             WithdrawalRepository withdrawalRepository,
                             TransactionRecordRepository transactionRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Withdrawal requestWithdrawal(Long projectId, Double amount, String bankDetails, String farmerEmail) {
        if (amount == null || amount <= 0) {
            throw new RuntimeException("Withdrawal amount must be greater than zero.");
        }
        if (bankDetails == null || bankDetails.isBlank()) {
            throw new RuntimeException("Bank details are mandatory.");
        }

        User farmer = userRepository.findByEmail(farmerEmail)
                .orElseThrow(() -> new RuntimeException("Farmer not found"));

        FarmProject project = projectRepository.findByIdWithLock(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (project.getFarmer() == null || !project.getFarmer().getId().equals(farmer.getId())) {
            throw new RuntimeException("You can only withdraw funds from your own project.");
        }

        BigDecimal amountValue = BigDecimal.valueOf(amount);
        BigDecimal allowed = project.getWithdrawableBalance() != null ? project.getWithdrawableBalance() : BigDecimal.ZERO;
        if (amountValue.compareTo(allowed) > 0) {
            throw new RuntimeException("Insufficient authorized funds! You can only withdraw up to Rs " + allowed);
        }
        BigDecimal walletBalance = farmer.getWalletBalance() != null ? farmer.getWalletBalance() : BigDecimal.ZERO;
        if (amountValue.compareTo(walletBalance) > 0) {
            throw new RuntimeException("Insufficient wallet balance! Available balance is Rs " + walletBalance);
        }

        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setAmount(amount);
        withdrawal.setStatus("COMPLETED");
        withdrawal.setRequestedAt(LocalDateTime.now());
        withdrawal.setBankDetails(bankDetails.trim());
        withdrawal.setProject(project);
        withdrawal.setUser(farmer);

        project.setWithdrawableBalance(allowed.subtract(amountValue));
        farmer.setWalletBalance(walletBalance.subtract(amountValue));

        userRepository.save(farmer);
        projectRepository.save(project);

        Withdrawal savedWithdrawal = withdrawalRepository.save(withdrawal);
        String refId = "TXN-WIT-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        transactionRepository.save(new TransactionRecord(refId, "WITHDRAWAL", amountValue, farmer));
        return savedWithdrawal;
    }

    @Transactional
    public Withdrawal requestWalletWithdrawal(Double amount, String bankDetails, String email) {
        if (amount == null) {
            throw new RuntimeException("Amount must be greater than zero");
        }
        User user = executeFarmerWithdrawal(email, BigDecimal.valueOf(amount), bankDetails);

        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setUser(user);
        withdrawal.setAmount(amount);
        withdrawal.setStatus("COMPLETED");
        withdrawal.setBankDetails(bankDetails.trim());
        withdrawal.setRequestedAt(LocalDateTime.now());

        Withdrawal savedWithdrawal = withdrawalRepository.save(withdrawal);
        String refId = "TXN-WIT-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        transactionRepository.save(new TransactionRecord(refId, "WITHDRAWAL", BigDecimal.valueOf(amount), user));
        return savedWithdrawal;
    }

    @Transactional
    public User executeFarmerWithdrawal(String email, BigDecimal withdrawalAmount, String bankDetails) {
        if (withdrawalAmount == null || withdrawalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }
        if (bankDetails == null || bankDetails.isBlank()) {
            throw new RuntimeException("Bank details or UPI ID mandatory");
        }

        User farmer = userRepository.findByEmailWithLock(email)
                .orElseThrow(() -> new RuntimeException("Farmer profile not found"));

        BigDecimal currentBalance = farmer.getWithdrawableBalance() != null
                ? farmer.getWithdrawableBalance()
                : BigDecimal.ZERO;

        if (currentBalance.compareTo(withdrawalAmount) < 0) {
            throw new IllegalArgumentException("Insufficient funds available for payout.");
        }

        farmer.setWithdrawableBalance(currentBalance.subtract(withdrawalAmount));
        return userRepository.save(farmer);
    }

    @Transactional(readOnly = true)
    public List<Withdrawal> getMyHistory(String email) {
        List<Withdrawal> userWithdrawals = withdrawalRepository.findByUserEmailOrderByRequestedAtDesc(email);
        if (!userWithdrawals.isEmpty()) {
            return userWithdrawals;
        }
        return withdrawalRepository.findByProjectFarmerEmailOrderByRequestedAtDesc(email);
    }
}
