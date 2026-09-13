package com.vanguard.accountsync.controller;

import com.vanguard.accountsync.model.DynamoDBRecord;
import com.vanguard.accountsync.model.MatchStatus;
import com.vanguard.accountsync.model.ReconciliationResult;
import com.vanguard.accountsync.service.EODReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/eod")
@RequiredArgsConstructor
@Slf4j
public class EODReconciliationController {

    private final EODReconciliationService eodReconciliationService;

    @PostMapping("/reconcile")
    public ResponseEntity<Map<String, Object>> reconcile(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate reconciliationDate = date != null ? date : LocalDate.now();
        try {
            ReconciliationResult result = eodReconciliationService.reconcile(reconciliationDate);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("reconciliationDate", result.getReconciliationDate().toString());
            response.put("status", result.getStatus());
            response.put("message", result.getMessage());
            response.put("totalEODRecords", result.getTotalEODRecords());
            response.put("matchedByPK", result.getMatchedByPK());
            response.put("matchedByFallback", result.getMatchedByFallback());
            response.put("unmatched", result.getUnmatched());
            response.put("driftCount", result.getDriftCount());
            response.put("driftRate", result.getDriftRate());
            response.put("processingTimeMs", result.getProcessingTimeMs());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Reconciliation failed for {}", reconciliationDate, ex);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("reconciliationDate", reconciliationDate.toString());
            response.put("status", "FAILURE");
            response.put("message", "Reconciliation failed: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/results/{date}")
    public ResponseEntity<Map<String, Object>> getResults(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Optional<ReconciliationResult> resultOpt = eodReconciliationService.getResults(date);
        if (resultOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ReconciliationResult result = resultOpt.get();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEODRecords", result.getTotalEODRecords());
        summary.put("matchedByPK", result.getMatchedByPK());
        summary.put("matchedByFallback", result.getMatchedByFallback());
        summary.put("unmatched", result.getUnmatched());
        summary.put("driftRate", result.getDriftRate());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalTime", result.getProcessingTimeMs() + "ms");
        report.put("status", result.getStatus());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reconciliationDate", date.toString());
        response.put("summary", summary);
        response.put("matches", result.getMatches().stream().map(this::toMatchView).toList());
        response.put("fallbackMatches", result.getFallbackMatches().stream().map(this::toMatchView).toList());
        response.put("unmatchedAccounts", result.getUnmatchedAccounts().stream().map(this::toUnmatchedView).toList());
        response.put("report", report);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/drifts")
    public ResponseEntity<Map<String, Object>> getDrifts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();
        List<DynamoDBRecord> drifts = eodReconciliationService.getDrifts(targetDate);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", targetDate.toString());
        response.put("driftCount", drifts.size());
        response.put("drifts", drifts);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "EOD Reconciliation Service");
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toMatchView(DynamoDBRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("eodRecordId", record.getPk());
        view.put("vastAccountNumber", record.getVastAccountNumber());
        view.put("portId", record.getPortId());
        view.put("newtAccountNumber", record.getNewtAccountNumber());
        view.put("matchType", record.getStatus() == MatchStatus.MATCHED ? "Direct Match" : "Fallback Match");
        view.put("matchedAt", record.getLastUpdated());
        return view;
    }

    private Map<String, Object> toUnmatchedView(DynamoDBRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("eodRecordId", record.getPk());
        view.put("vastAccountNumber", record.getVastAccountNumber());
        view.put("portId", record.getPortId());
        view.put("reason", record.getStatus().name());
        view.put("createdAt", record.getCreatedAt());
        return view;
    }
}
