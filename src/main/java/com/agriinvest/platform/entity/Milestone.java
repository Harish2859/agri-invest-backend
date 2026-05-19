package com.agriinvest.platform.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "milestones")
@Data
public class Milestone {

    private LocalDateTime submittedAt;

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    // This field must be named 'farmProject' to match milestone.getFarmProject()
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id")
    @JsonAlias("project")
    private FarmProject farmProject;


    private String title; // e.g., "Seeds Sown"

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, COMPLETED, REJECTED

    @Column(nullable = false)
    private Double releasePercentage; // e.g., 20.0 (The % of budget this unblocks)

    private boolean fundsReleased = false; // The single "truth" for payment status

    private Double amountToRelease; // The actual calculated currency value

    // Verification Logic (For the Village Head)
    private boolean isVerified = false;
    private String proofImageUrl;
    private LocalDateTime verifiedAt;

    @ManyToOne
    @JoinColumn(name = "verified_by_id")
    private User verifiedBy; // The Village Lead who checked it

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    @JsonIgnore
    public FarmProject getProject() {
        return farmProject;
    }

    public void setProject(FarmProject project) {
        this.farmProject = project;
    }
}
