package com.agriinvest.platform.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_records")
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", unique = true, nullable = false)
    private String referenceId;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    @Column(name = "precision_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal precisionAmount;

    @Column(name = "localized_timestamp", nullable = false)
    private LocalDateTime localizedTimestamp;

    @Column(name = "processing_status", nullable = false)
    private String processingStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public TransactionRecord() {}

    public TransactionRecord(String referenceId, String transactionType, BigDecimal precisionAmount, User user) {
        this.referenceId = referenceId;
        this.transactionType = transactionType;
        this.precisionAmount = precisionAmount;
        this.localizedTimestamp = LocalDateTime.now();
        this.processingStatus = "SUCCESS";
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public BigDecimal getPrecisionAmount() { return precisionAmount; }
    public void setPrecisionAmount(BigDecimal precisionAmount) { this.precisionAmount = precisionAmount; }

    public LocalDateTime getLocalizedTimestamp() { return localizedTimestamp; }
    public void setLocalizedTimestamp(LocalDateTime localizedTimestamp) { this.localizedTimestamp = localizedTimestamp; }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
