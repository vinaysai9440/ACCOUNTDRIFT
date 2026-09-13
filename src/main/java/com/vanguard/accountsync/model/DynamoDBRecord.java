package com.vanguard.accountsync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents an account transfer record in the NewT platform (DynamoDB), mirrored locally
 * in H2 for development. The pk mirrors the DynamoDB partition key: vastAccountNumber|portId|createdDate.
 */
@Entity
@Table(name = "account_transfers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamoDBRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pk", nullable = false, unique = true)
    private String pk;

    @Column(name = "sk")
    private String sk;

    @Column(name = "vast_account_number", nullable = false)
    private String vastAccountNumber;

    @Column(name = "port_id", nullable = false)
    private String portId;

    @Column(name = "newt_account_number")
    private String newtAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MatchStatus status;

    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "is_matched")
    private Boolean isMatched;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /**
     * The EOD business date this record was last reconciled against.
     * Distinct from the date embedded in {@code pk}, which reflects when the account
     * was originally transferred to NewT.
     */
    @Column(name = "reconciliation_date")
    private LocalDate reconciliationDate;

    // Lombok would otherwise name these getIsMatched/setIsMatched inconsistently
    // depending on version; declare explicitly so JSON serializes as "isMatched".
    public Boolean getIsMatched() {
        return isMatched;
    }

    public void setIsMatched(Boolean isMatched) {
        this.isMatched = isMatched;
    }
}
