package com.vanguard.accountsync.repository;

import com.vanguard.accountsync.model.EODAccountRecord;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Mocked access layer for the DB2 "EOD SP" stored procedure that produces the daily
 * batch of accounts created on the legacy VAST platform.
 *
 * <p>Generates a deterministic set of 100 accounts per date so reconciliation behavior
 * is reproducible: the first {@link #DIRECT_MATCH_COUNT} accounts are designed to match
 * NewT by primary key, the next {@link #FALLBACK_MATCH_COUNT} are designed to match only
 * via the vastAccountNumber + portId fallback, and the remainder are designed to have no
 * corresponding NewT record at all (drifts).
 *
 * <p>Account numbers embed the EOD date so each day's batch is its own account universe,
 * matching real DB2 behavior where every day introduces newly created accounts rather than
 * reusing prior days' account numbers.
 */
@Repository
public class EODSPRepository {

    private static final int TOTAL_ACCOUNTS = 100;
    private static final int DIRECT_MATCH_COUNT = 80;
    private static final int FALLBACK_MATCH_COUNT = 15;
    private static final DateTimeFormatter DATE_SUFFIX = DateTimeFormatter.BASIC_ISO_DATE;

    @Retry(name = "eodDataFetch")
    public List<EODAccountRecord> fetchAccountsForDate(LocalDate date) {
        List<EODAccountRecord> records = new ArrayList<>(TOTAL_ACCOUNTS);
        String dateSuffix = date.format(DATE_SUFFIX);
        for (int i = 1; i <= TOTAL_ACCOUNTS; i++) {
            String vastAccountNumber = String.format("ACC%s%03d", dateSuffix, i);
            String portId = String.format("PORT%03d", (i - 1) % 10);
            records.add(EODAccountRecord.builder()
                    .vastAccountNumber(vastAccountNumber)
                    .portId(portId)
                    .createdDate(date)
                    .build());
        }
        return records;
    }

    public int getTotalAccounts() {
        return TOTAL_ACCOUNTS;
    }

    public int getDirectMatchCount() {
        return DIRECT_MATCH_COUNT;
    }

    public int getFallbackMatchCount() {
        return FALLBACK_MATCH_COUNT;
    }

    public int getDriftCount() {
        return TOTAL_ACCOUNTS - DIRECT_MATCH_COUNT - FALLBACK_MATCH_COUNT;
    }
}
