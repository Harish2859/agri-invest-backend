package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.FarmProject;
import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.entity.Withdrawal;
import com.agriinvest.platform.repository.ProjectRepository;
import com.agriinvest.platform.repository.UserRepository;
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

    @Autowired
    public WithdrawalService(ProjectRepository projectRepository,
                             UserRepository userRepository,
                             WithdrawalRepository withdrawalRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.withdrawalRepository = withdrawalRepository;
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

        return withdrawalRepository.save(withdrawal);
    }

    @Transactional
    public Withdrawal requestWalletWithdrawal(Double amount, String bankDetails, String email) {
        if (amount == null || amount <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }
        if (bankDetails == null || bankDetails.isBlank()) {
            throw new RuntimeException("Bank details or UPI ID mandatory");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal amountValue = BigDecimal.valueOf(amount);
        BigDecimal walletBalance = user.getWalletBalance() != null ? user.getWalletBalance() : BigDecimal.ZERO;
        if (amountValue.compareTo(walletBalance) > 0) {
            throw new RuntimeException("Insufficient balance for withdrawal");
        }

        user.setWalletBalance(walletBalance.subtract(amountValue));
        userRepository.save(user);

        Withdrawal withdrawal = new Withdrawal();
        withdrawal.setUser(user);
        withdrawal.setAmount(amount);
        withdrawal.setStatus("COMPLETED");
        withdrawal.setBankDetails(bankDetails.trim());
        withdrawal.setRequestedAt(LocalDateTime.now());

        return withdrawalRepository.save(withdrawal);
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
