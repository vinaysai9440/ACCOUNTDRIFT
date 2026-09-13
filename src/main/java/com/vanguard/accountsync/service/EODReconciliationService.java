package com.vanguard.accountsync.service;

import com.vanguard.accountsync.model.DynamoDBRecord;
import com.vanguard.accountsync.model.EODAccountRecord;
import com.vanguard.accountsync.model.MatchStatus;
import com.vanguard.accountsync.model.ReconciliationResult;
import com.vanguard.accountsync.repository.DynamoDBRepository;
import com.vanguard.accountsync.repository.EODSPRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates a full EOD reconciliation run: fetches the DB2 EOD SP batch, fetches the
 * current NewT (DynamoDB) snapshot, coordinates per-account matching, and produces a
 * {@link ReconciliationResult} summarizing the outcome.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EODReconciliationService {

    private final EODSPRepository eodspRepository;
    private final DynamoDBRepository dynamoDBRepository;
    private final AccountMatchingService accountMatchingService;

    private final Map<LocalDate, ReconciliationResult> latestResults = new ConcurrentHashMap<>();

    public ReconciliationResult reconcile(LocalDate date) {
        long start = System.currentTimeMillis();
        log.info("Starting EOD reconciliation for {}", date);

        List<EODAccountRecord> eodRecords = eodspRepository.fetchAccountsForDate(date);

        accountMatchingService.seedTransferredAccountsIfNeeded(
                date, eodRecords, eodspRepository.getDirectMatchCount(), eodspRepository.getFallbackMatchCount());

        List<DynamoDBRecord> snapshot = accountMatchingService.loadDynamoDbSnapshot();
        Map<String, DynamoDBRecord> byPk = accountMatchingService.indexByPk(snapshot);
        Map<String, DynamoDBRecord> byVastAccountAndPort = accountMatchingService.indexByVastAccountAndPort(snapshot);

        List<DynamoDBRecord> matches = new ArrayList<>();
        List<DynamoDBRecord> fallbackMatches = new ArrayList<>();
        List<DynamoDBRecord> unmatchedAccounts = new ArrayList<>();

        for (EODAccountRecord eodRecord : eodRecords) {
            AccountMatchingService.MatchOutcome outcome =
                    accountMatchingService.matchAccount(eodRecord, date, byPk, byVastAccountAndPort);
            switch (outcome.status()) {
                case MATCHED -> matches.add(outcome.record());
                case MATCHED_FALLBACK -> fallbackMatches.add(outcome.record());
                case VAST_ONLY -> unmatchedAccounts.add(outcome.record());
                default -> log.warn("Unexpected match status {} for account {}", outcome.status(), eodRecord.getPk());
            }
        }

        long processingTime = System.currentTimeMillis() - start;
        ReconciliationResult result = buildResult(
                date, eodRecords.size(), matches, fallbackMatches, unmatchedAccounts, processingTime);

        latestResults.put(date, result);
        log.info(result.getMessage());
        return result;
    }

    public Optional<ReconciliationResult> getResults(LocalDate date) {
        ReconciliationResult cached = latestResults.get(date);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (!dynamoDBRepository.existsByReconciliationDate(date)) {
            return Optional.empty();
        }
        return Optional.of(buildResultFromRepository(date));
    }

    public List<DynamoDBRecord> getDrifts(LocalDate date) {
        return dynamoDBRepository.findByReconciliationDateAndStatus(date, MatchStatus.VAST_ONLY);
    }

    private ReconciliationResult buildResultFromRepository(LocalDate date) {
        List<DynamoDBRecord> all = dynamoDBRepository.findByReconciliationDate(date);
        List<DynamoDBRecord> matched = all.stream().filter(r -> r.getStatus() == MatchStatus.MATCHED).toList();
        List<DynamoDBRecord> fallback = all.stream().filter(r -> r.getStatus() == MatchStatus.MATCHED_FALLBACK).toList();
        List<DynamoDBRecord> unmatched = all.stream().filter(r -> r.getStatus() == MatchStatus.VAST_ONLY).toList();
        int total = matched.size() + fallback.size() + unmatched.size();
        return buildResult(date, total, matched, fallback, unmatched, 0);
    }

    private ReconciliationResult buildResult(LocalDate date,
                                              int total,
                                              List<DynamoDBRecord> matches,
                                              List<DynamoDBRecord> fallbackMatches,
                                              List<DynamoDBRecord> unmatchedAccounts,
                                              long processingTimeMs) {
        int unmatchedCount = unmatchedAccounts.size();
        double driftRateValue = total == 0 ? 0.0 : (unmatchedCount * 100.0) / total;

        return ReconciliationResult.builder()
                .reconciliationDate(date)
                .status("SUCCESS")
                .message(String.format(
                        "Reconciliation completed. Total: %d, Matched: %d, Fallback: %d, Drifts: %d (%.2f%%)",
                        total, matches.size(), fallbackMatches.size(), unmatchedCount, driftRateValue))
                .totalEODRecords(total)
                .matchedByPK(matches.size())
                .matchedByFallback(fallbackMatches.size())
                .unmatched(unmatchedCount)
                .processingTimeMs(processingTimeMs)
                .matches(matches)
                .fallbackMatches(fallbackMatches)
                .unmatchedAccounts(unmatchedAccounts)
                .build();
    }
}
