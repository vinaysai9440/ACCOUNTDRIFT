package com.vanguard.accountsync.service;

import com.vanguard.accountsync.model.DynamoDBRecord;
import com.vanguard.accountsync.model.EODAccountRecord;
import com.vanguard.accountsync.model.MatchStatus;
import com.vanguard.accountsync.repository.DynamoDBRepository;
import com.vanguard.accountsync.util.AccountNumberGenerator;
import com.vanguard.accountsync.util.PKGenerator;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements the account matching algorithm between DB2 EOD SP records and NewT (DynamoDB):
 *
 * <ul>
 *   <li><b>Case A</b> - Direct PK match: the EOD record's PK exists in NewT. If the NewT
 *       record has no newtAccountNumber yet, one is generated. Status becomes MATCHED.</li>
 *   <li><b>Case B</b> - Fallback match: the PK is not found, but a NewT record with the
 *       same vastAccountNumber + portId exists. Status becomes MATCHED_FALLBACK.</li>
 *   <li><b>Case C</b> - No match: neither lookup succeeds. A new VAST_ONLY record is
 *       created, representing a drift that needs investigation.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountMatchingService {

    private final DynamoDBRepository dynamoDBRepository;

    public record MatchOutcome(MatchStatus status, DynamoDBRecord record) {
    }

    /**
     * Fetches the current NewT (DynamoDB) snapshot used to build the in-memory lookup
     * indexes for a reconciliation run. Wrapped in retry to simulate resilience against
     * transient DynamoDB read failures.
     */
    @Retry(name = "dynamoDataFetch")
    public List<DynamoDBRecord> loadDynamoDbSnapshot() {
        log.debug("Fetching current NewT (DynamoDB) snapshot for matching");
        // VAST_ONLY rows are locally-tracked drift placeholders, not real NewT transfers,
        // so they must never be treated as a candidate match for a later reconciliation run.
        return dynamoDBRepository.findAll().stream()
                .filter(r -> r.getStatus() != MatchStatus.VAST_ONLY)
                .toList();
    }

    public Map<String, DynamoDBRecord> indexByPk(List<DynamoDBRecord> snapshot) {
        return snapshot.stream()
                .collect(Collectors.toMap(DynamoDBRecord::getPk, r -> r, (a, b) -> a));
    }

    public Map<String, DynamoDBRecord> indexByVastAccountAndPort(List<DynamoDBRecord> snapshot) {
        Map<String, DynamoDBRecord> index = new HashMap<>();
        for (DynamoDBRecord r : snapshot) {
            index.putIfAbsent(PKGenerator.vastPortKey(r.getVastAccountNumber(), r.getPortId()), r);
        }
        return index;
    }

    @Transactional
    public MatchOutcome matchAccount(EODAccountRecord eodRecord,
                                      LocalDate reconciliationDate,
                                      Map<String, DynamoDBRecord> byPk,
                                      Map<String, DynamoDBRecord> byVastAccountAndPort) {
        String pk = eodRecord.getPk();
        String vastPortKey = PKGenerator.vastPortKey(eodRecord.getVastAccountNumber(), eodRecord.getPortId());

        DynamoDBRecord match = byPk.get(pk);
        MatchStatus matchedStatus = MatchStatus.MATCHED;

        if (match == null) {
            match = byVastAccountAndPort.get(vastPortKey);
            matchedStatus = MatchStatus.MATCHED_FALLBACK;
        }

        if (match != null) {
            if (isBlank(match.getNewtAccountNumber())) {
                match.setNewtAccountNumber(AccountNumberGenerator.generate());
            }
            match.setStatus(matchedStatus);
            match.setIsMatched(true);
            match.setReconciliationDate(reconciliationDate);
            match.setLastUpdated(LocalDateTime.now());
            DynamoDBRecord saved = dynamoDBRepository.save(match);
            return new MatchOutcome(matchedStatus, saved);
        }

        LocalDateTime now = LocalDateTime.now();

        // A drift for this exact pk may already exist from a prior reconciliation run
        // of the same date; refresh it in place instead of violating the pk uniqueness
        // constraint with a duplicate insert.
        DynamoDBRecord drift = dynamoDBRepository.findByPk(pk).orElseGet(() -> DynamoDBRecord.builder()
                .pk(pk)
                .vastAccountNumber(eodRecord.getVastAccountNumber())
                .portId(eodRecord.getPortId())
                .createdAt(now)
                .build());
        drift.setNewtAccountNumber(null);
        drift.setStatus(MatchStatus.VAST_ONLY);
        drift.setIsMatched(false);
        drift.setReconciliationDate(reconciliationDate);
        drift.setLastUpdated(now);
        drift.setErrorMessage("Account present in EOD SP (DB2) but not found in NewT (DynamoDB)");
        DynamoDBRecord saved = dynamoDBRepository.save(drift);
        return new MatchOutcome(MatchStatus.VAST_ONLY, saved);
    }

    /**
     * Seeds NewT (H2/DynamoDB) with accounts that were already transferred prior to this
     * EOD run, so that local development has realistic data to reconcile against without
     * a live DynamoDB connection. A no-op once a date has already been seeded.
     */
    @Transactional
    public void seedTransferredAccountsIfNeeded(LocalDate date,
                                                 List<EODAccountRecord> eodRecords,
                                                 int directMatchCount,
                                                 int fallbackMatchCount) {
        if (dynamoDBRepository.existsByReconciliationDate(date)) {
            log.debug("NewT already seeded for {}, skipping", date);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int index = 0;
        for (EODAccountRecord eod : eodRecords) {
            index++;
            if (index <= directMatchCount) {
                boolean withholdNewtAccountNumber = index > directMatchCount - 10;
                dynamoDBRepository.save(DynamoDBRecord.builder()
                        .pk(eod.getPk())
                        .sk("TRANSFER#" + eod.getCreatedDate())
                        .vastAccountNumber(eod.getVastAccountNumber())
                        .portId(eod.getPortId())
                        .newtAccountNumber(withholdNewtAccountNumber ? null : AccountNumberGenerator.generate())
                        .status(MatchStatus.MATCHED)
                        .transferredAt(now)
                        .createdAt(now)
                        .lastUpdated(now)
                        .isMatched(true)
                        .reconciliationDate(date)
                        .build());
            } else if (index <= directMatchCount + fallbackMatchCount) {
                LocalDate transferredDate = eod.getCreatedDate().minusDays(1);
                dynamoDBRepository.save(DynamoDBRecord.builder()
                        .pk(PKGenerator.generate(eod.getVastAccountNumber(), eod.getPortId(), transferredDate))
                        .sk("TRANSFER#" + transferredDate)
                        .vastAccountNumber(eod.getVastAccountNumber())
                        .portId(eod.getPortId())
                        .newtAccountNumber(AccountNumberGenerator.generate())
                        .status(MatchStatus.MATCHED)
                        .transferredAt(now)
                        .createdAt(now)
                        .lastUpdated(now)
                        .isMatched(true)
                        .reconciliationDate(transferredDate)
                        .build());
            }
            // Remaining accounts are intentionally left absent from NewT to simulate drifts.
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
