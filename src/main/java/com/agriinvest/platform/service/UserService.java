package com.agriinvest.platform.service;

import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.entity.KycStatus;
import com.agriinvest.platform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder; // Add this
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // Add this

    public User registerUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setVerified(false);
        user.setKycStatus(KycStatus.PENDING);
        user.setKycRejectionReason(null);
        user.setKycVerifiedAt(null);

        // Hard-set the role if it's missing to ensure the DB record is clean
        if (user.getRole() == null) {
            user.setRole(User.Role.INVESTOR);
        }

        return userRepository.save(user);
    }

    // Add this helper method for the Login process later
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
