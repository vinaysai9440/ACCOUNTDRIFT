# Quick Start Guide

## What You Have

A complete EOD Reconciliation Service with:
- Java 17 / Spring Boot 3.2 code, ready to run locally
- Deterministic mock data (100 test accounts per date, covering all 3 match cases)
- Retry mechanism (Resilience4j exponential backoff)
- 4 REST endpoints
- Integration tests
- H2 in-memory database — no setup needed

## Getting Started (5 minutes)

### 1. Build
```bash
mvn clean install
```

### 2. Run
```bash
mvn spring-boot:run
```
Starts on `http://localhost:8080`.

### 3. Test in another terminal
```bash
curl -X POST "http://localhost:8080/api/eod/reconcile?date=2026-09-13"
curl "http://localhost:8080/api/eod/results/2026-09-13"
curl "http://localhost:8080/api/eod/drifts?date=2026-09-13"
curl http://localhost:8080/api/eod/health
```

## Expected Test Results

```
Total Accounts: 100
├─ Matched by PK: 80
├─ Matched by Fallback: 15
└─ Unmatched (VAST_ONLY): 5   ← drifts, ready for agent investigation

Drift Rate: 5.00%
Status: SUCCESS
```

## Reconciliation Logic

```
For each account in EOD SP:
├─ Is PK in DynamoDB snapshot?
│  ├─ YES → Match by PK (80 accounts)
│  └─ NO  → continue
├─ Is vastAccountNumber + portId in DynamoDB snapshot?
│  ├─ YES → Match by Fallback (15 accounts)
│  └─ NO  → continue
└─ Create new VAST_ONLY record (5 accounts — DRIFTS)
```

## API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/eod/reconcile` | Trigger reconciliation for a date |
| GET | `/api/eod/results/{date}` | Get detailed results |
| GET | `/api/eod/drifts` | Get unmatched accounts (drifts) |
| GET | `/api/eod/health` | Health check |

## Database

- Type: H2 (in-memory), auto-created on startup
- Console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:accountsyncdb`
- Username: `sa`, password: blank

## Run Tests

```bash
mvn test
```

## Troubleshooting

**Port 8080 already in use**
```bash
lsof -i :8080
kill -9 <PID>
```

**Maven build fails**
```bash
mvn clean -U install
```

**Need debug logs** — already on by default (`com.vanguard.accountsync: DEBUG` in
`application.yml`).

## Success Criteria

1. `mvn clean install` completes without errors
2. `mvn spring-boot:run` starts the app
3. `curl http://localhost:8080/api/eod/health` returns `{"status":"UP", ...}`
4. `curl -X POST .../api/eod/reconcile` returns a reconciliation report
5. Report shows 80 matched + 15 fallback + 5 unmatched = 100 total
6. `mvn test` passes all integration tests
