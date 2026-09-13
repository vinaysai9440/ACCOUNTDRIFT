package com.vanguard.accountsync.integration;

import com.vanguard.accountsync.model.DynamoDBRecord;
import com.vanguard.accountsync.model.MatchStatus;
import com.vanguard.accountsync.model.ReconciliationResult;
import com.vanguard.accountsync.service.EODReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EODReconciliationIntegrationTest {

    @Autowired
    private EODReconciliationService eodReconciliationService;

    @Test
    void testEndToEndReconciliation() {
        LocalDate date = LocalDate.of(2026, 9, 13);

        ReconciliationResult result = eodReconciliationService.reconcile(date);

        assertThat(result.getTotalEODRecords()).isEqualTo(100);
        assertThat(result.getMatchedByPK()).isEqualTo(80);
        assertThat(result.getMatchedByFallback()).isEqualTo(15);
        assertThat(result.getUnmatched()).isEqualTo(5);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void testReconciliationMessage() {
        LocalDate date = LocalDate.of(2026, 9, 14);

        ReconciliationResult result = eodReconciliationService.reconcile(date);

        assertThat(result.getMessage()).contains("Total: 100");
        assertThat(result.getMessage()).contains("Matched: 80");
        assertThat(result.getMessage()).contains("Fallback: 15");
        assertThat(result.getMessage()).contains("Drifts: 5");
        assertThat(result.getDriftRate()).isEqualTo("5.00%");
    }

    @Test
    void testDriftRetrieval() {
        LocalDate date = LocalDate.of(2026, 9, 15);
        eodReconciliationService.reconcile(date);

        List<DynamoDBRecord> drifts = eodReconciliationService.getDrifts(date);

        assertThat(drifts).hasSize(5);
        assertThat(drifts).allMatch(d -> d.getStatus() == MatchStatus.VAST_ONLY);
        assertThat(drifts).allMatch(d -> !d.getIsMatched());
    }

    @Test
    void testGetResultsAfterReconciliation() {
        LocalDate date = LocalDate.of(2026, 9, 16);
        eodReconciliationService.reconcile(date);

        Optional<ReconciliationResult> results = eodReconciliationService.getResults(date);

        assertThat(results).isPresent();
        assertThat(results.get().getMatches()).hasSize(80);
        assertThat(results.get().getFallbackMatches()).hasSize(15);
        assertThat(results.get().getUnmatchedAccounts()).hasSize(5);
    }

    @Test
    void testGetResultsForUnknownDateIsEmpty() {
        Optional<ReconciliationResult> results = eodReconciliationService.getResults(LocalDate.of(1999, 1, 1));

        assertThat(results).isEmpty();
    }
}
