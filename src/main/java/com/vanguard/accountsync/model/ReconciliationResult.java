package com.vanguard.accountsync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of a single EOD reconciliation run, including per-account results
 * for direct matches, fallback matches, and unmatched (drift) accounts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationResult {

    private LocalDate reconciliationDate;
    private String status;
    private String message;

    private int totalEODRecords;
    private int matchedByPK;
    private int matchedByFallback;
    private int unmatched;
    private long processingTimeMs;

    @Builder.Default
    private List<DynamoDBRecord> matches = new ArrayList<>();

    @Builder.Default
    private List<DynamoDBRecord> fallbackMatches = new ArrayList<>();

    @Builder.Default
    private List<DynamoDBRecord> unmatchedAccounts = new ArrayList<>();

    public int getDriftCount() {
        return unmatched;
    }

    public String getDriftRate() {
        if (totalEODRecords == 0) {
            return "0.00%";
        }
        double rate = (unmatched * 100.0) / totalEODRecords;
        return String.format("%.2f%%", rate);
    }
}
