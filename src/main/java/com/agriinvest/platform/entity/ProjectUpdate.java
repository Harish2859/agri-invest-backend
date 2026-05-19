package com.agriinvest.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class ProjectUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private FarmProject project;

    @Column(length = 1000)
    private String message;

    private String updateImageUrl; // Optional: Farmer can add a photo
    private LocalDateTime createdAt;
}