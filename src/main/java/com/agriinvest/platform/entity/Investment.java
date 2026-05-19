package com.agriinvest.platform.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "investments")
@Data
public class Investment {

    private String status; // PENDING, COMPLETED, FAILED
    private String transactionId; // From UPI/Razorpay/Stripe
    private LocalDateTime investedAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JsonIgnoreProperties({"role", "password", "authorities", "enabled", "roleName"})
    private User investor;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private FarmProject project;

    @Column(nullable = false)
    private Double amountInvested;

    private Double finalReturn;

    @Column(nullable = false)
    private boolean settled = false;

    private LocalDateTime investmentDate = LocalDateTime.now();

    @JsonProperty("projectTitle")
    public String getProjectTitle() {
        return project != null ? project.getTitle() : null;
    }
}
