package com.agriinvest.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Data
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(unique = true)
    private String aadhaarNo;

    @Column(name = "kyc_document_url")
    private String kycDocumentUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_rejection_reason")
    private String kycRejectionReason;

    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @Column(name = "wallet_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal walletBalance = BigDecimal.ZERO;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Role {
        INVESTOR, FARMER, VILLAGE_LEAD
    }

    public String getRoleName() {
        return (this.role != null) ? this.role.name() : "NONE";
    }

    // --- Spring Security Methods ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return List.of();
        }

        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
