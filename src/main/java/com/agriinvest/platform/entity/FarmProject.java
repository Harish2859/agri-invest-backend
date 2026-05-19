package com.agriinvest.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "farm_projects")
@Data
public class FarmProject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    @ManyToOne
    @JoinColumn(name = "farmer_id")
    private User farmer;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal escrowBalance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal currentFunding = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal withdrawableBalance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal releasedToFarmer = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal finalFarmerProfit = BigDecimal.ZERO;

    @Column(nullable = false)
    private String cropType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    private Double equityOffered;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "land_image_url")
    private String landImageUrl;

    @Column(name = "min_investment_amount")
    private Double minInvestmentAmount = 1000.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.PENDING;

    private LocalDate startDate;
    private LocalDate endDate;
    private String location;
}
