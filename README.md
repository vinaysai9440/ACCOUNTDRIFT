# ACCOUNTDRIFT — Account Sync EOD Reconciliation Service

End-of-Day (EOD) reconciliation service for an Account Sync platform. This service compares
account data from DB2 (EOD SP) with transferred accounts in DynamoDB (NewT) to identify and
report reconciliation drifts.

## Project Overview

### Purpose
Automate the daily reconciliation process between a legacy platform (DB2) and a new platform
(AWS/DynamoDB, "NewT") to:
- Verify all accounts created in DB2 are successfully transferred to NewT
- Identify discrepancies (drifts) for investigation
- Generate detailed reconciliation reports
- Provide a foundation for AI-powered drift investigation (agent integration)

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│          Spring Boot Application (Port 8080)            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  REST API Controller                                    │
│  ├─ POST /api/eod/reconcile        (Trigger reconciliation)
│  ├─ GET  /api/eod/results/{date}   (Get detailed results)
│  ├─ GET  /api/eod/drifts           (Get drifts/VAST_ONLY)
│  └─ GET  /api/eod/health           (Health check)
│                                                         │
│  EOD Reconciliation Service (Orchestrator)             │
│  ├─ Fetch EOD SP data (with retry)                     │
│  ├─ Fetch DynamoDB snapshot (with retry)               │
│  └─ Coordinate matching                                 │
│                                                         │
│  Account Matching Service                               │
│  ├─ Case A: Match by PK                                │
│  ├─ Case B: Match by fallback key                      │
│  └─ Case C: Create VAST_ONLY (drift)                   │
│                                                         │
│  Data Access Layer                                      │
│  ├─ EOD SP Repository (mocked DB2)                     │
│  ├─ DynamoDB Repository (H2 local)                     │
│  └─ Retry configuration (Resilience4j)                 │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.0
- **Database**: H2 (in-memory, local development)
- **Retry**: Resilience4j 2.1.0 (exponential backoff)
- **Testing**: JUnit 5, AssertJ
- **Build**: Maven 3.8+

## Quick Start

### Prerequisites
- Java 17 or higher
- Maven 3.8 or higher

### Build

```bash
mvn clean install
```

### Run

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

### Verify Installation
```bash
curl http://localhost:8080/api/eod/health
# {"status":"UP","service":"EOD Reconciliation Service"}
```

## API Usage

### 1. Trigger Reconciliation

```bash
# Reconcile today
curl -X POST http://localhost:8080/api/eod/reconcile

# Reconcile a specific date
curl -X POST "http://localhost:8080/api/eod/reconcile?date=2026-09-13"
```

**Response:**
```json
{
  "success": true,
  "reconciliationDate": "2026-09-13",
  "status": "SUCCESS",
  "message": "Reconciliation completed. Total: 100, Matched: 80, Fallback: 15, Drifts: 5 (5.00%)",
  "totalEODRecords": 100,
  "matchedByPK": 80,
  "matchedByFallback": 15,
  "unmatched": 5,
  "driftCount": 5,
  "driftRate": "5.00%",
  "processingTimeMs": 234
}
```

### 2. Get Reconciliation Results

```bash
curl http://localhost:8080/api/eod/results/2026-09-13
```

Returns a summary, the list of direct matches, fallback matches, and unmatched (drift)
accounts for that date.

### 3. Get Drifts

```bash
curl http://localhost:8080/api/eod/drifts
curl "http://localhost:8080/api/eod/drifts?date=2026-09-13"
```

Returns all `VAST_ONLY` accounts — present in DB2 EOD SP but never transferred to NewT —
that need investigation.

### 4. Health Check

```bash
curl http://localhost:8080/api/eod/health
```

## Test Data

`EODSPRepository` generates a deterministic set of 100 mock accounts per reconciliation
date:
- 80 accounts: match by PK in DynamoDB
- 15 accounts: match by fallback key (vastAccountNumber + portId)
- 5 accounts: no match (VAST_ONLY — drifts)

Expected report: Total 100, Matched by PK 80, Matched by Fallback 15, Drifts 5 (5.00%),
Status SUCCESS.

## Database

H2 in-memory database — no external setup needed.

```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:accountsyncdb
Username: sa
Password: (leave empty)
```

### Schema — `account_transfers`

| Column | Type | Notes |
|---|---|---|
| id | BIGINT | Primary key (identity) |
| pk | VARCHAR | Unique. `vastAccountNumber\|portId\|createdDate` |
| sk | VARCHAR | |
| vast_account_number | VARCHAR | |
| port_id | VARCHAR | |
| newt_account_number | VARCHAR | Auto-generated when missing at match time |
| status | VARCHAR | `PENDING`, `MATCHED`, `MATCHED_FALLBACK`, `VAST_ONLY` |
| transferred_at | TIMESTAMP | |
| created_at | TIMESTAMP | |
| last_updated | TIMESTAMP | |
| is_matched | BOOLEAN | |
| error_message | VARCHAR | |
| reconciliation_date | DATE | The EOD business date last reconciled against |

## Key Concepts

### Primary Key (PK) Generation
PK Format: `vastAccountNumber|portId|createdDate`, e.g. `ACC00001|PORT000|2026-09-13`.

### Matching Logic

**Case A — Direct PK Match**: The EOD record's PK is found in NewT. If `newtAccountNumber`
is missing it is generated. Status → `MATCHED`.

**Case B — Fallback Match**: The PK is not found, but a NewT record with the same
`vastAccountNumber` + `portId` exists. Status → `MATCHED_FALLBACK`.

**Case C — No Match (Drift)**: Neither lookup succeeds. A new record is created with
status `VAST_ONLY`, flagged for investigation.

### Retry Mechanism
`EODSPRepository.fetchAccountsForDate` and `AccountMatchingService.loadDynamoDbSnapshot`
are wrapped with Resilience4j `@Retry` (instances `eodDataFetch` / `dynamoDataFetch`),
configured for 3 attempts with exponential backoff (1s → 2s → 4s) on `IOException` /
`TimeoutException`.

## Running Tests

```bash
mvn test
```

## Project Structure

```
ACCOUNTDRIFT/
├── pom.xml
├── README.md
├── QUICK_START.md
├── DELIVERABLES.md
├── .gitignore
├── src/
│   ├── main/
│   │   ├── java/com/vanguard/accountsync/
│   │   │   ├── AccountSyncEODApplication.java
│   │   │   ├── controller/
│   │   │   │   └── EODReconciliationController.java
│   │   │   ├── service/
│   │   │   │   ├── EODReconciliationService.java
│   │   │   │   └── AccountMatchingService.java
│   │   │   ├── repository/
│   │   │   │   ├── EODSPRepository.java
│   │   │   │   └── DynamoDBRepository.java
│   │   │   ├── model/
│   │   │   │   ├── EODAccountRecord.java
│   │   │   │   ├── DynamoDBRecord.java
│   │   │   │   ├── ReconciliationResult.java
│   │   │   │   └── MatchStatus.java
│   │   │   ├── util/
│   │   │   │   ├── PKGenerator.java
│   │   │   │   └── AccountNumberGenerator.java
│   │   │   └── config/
│   │   │       └── RetryConfig.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/vanguard/accountsync/
│           └── integration/
│               └── EODReconciliationIntegrationTest.java
```

## Next Steps: AI Agent Integration

This service is the foundation for AI agent integration:
1. **Daily Report Agent** — generates intelligent reconciliation reports
2. **Drift Investigation Agent** — auto-investigates unmatched accounts from `/api/eod/drifts`
3. **Root Cause Analysis** — determines why a transfer failed
4. **Remediation Suggestions** — recommends fixes for each drift

## Troubleshooting

**Build fails** — `mvn clean -U install`

**Port 8080 in use** — `lsof -i :8080` then stop the process, or change `server.port` in
`application.yml`.

**Need debug logs** — set `logging.level.com.vanguard.accountsync: DEBUG` in
`application.yml` (already the default).
