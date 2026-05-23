package com.agriinvest.platform.repository;

import com.agriinvest.platform.entity.User;
import com.agriinvest.platform.entity.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    List<User> findByVerifiedFalseAndRoleOrderByCreatedAtAsc(User.Role role);

    List<User> findByKycStatusAndRoleOrderByCreatedAtAsc(KycStatus kycStatus, User.Role role);
}
