package com.agriinvest.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data // This automatically generates getters, setters, and toString
public class Withdrawal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id") // Best practice for foreign keys
    private FarmProject project;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private Double amount;
    private String status; // PENDING, COMPLETED, REJECTED
    private LocalDateTime requestedAt;

    // Updated to match the Service/Controller logic
    @Column(length = 500)
    private String bankDetails;

    // You can keep this or remove it since bankDetails covers it
    private String bankAccountNumber;
}
