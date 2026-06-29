package com.fraudlens.controller;

//import com.fraudlens.graph.TransactionGraph;
//import com.fraudlens.model.Account;
import com.fraudlens.model.FraudAlert;
import com.fraudlens.model.Transaction;
import com.fraudlens.service.FraudAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController  // class handels HTTP requests and returns JSON responses
@CrossOrigin
@RequestMapping("/api")
public class FraudController {

    @Autowired
    private FraudAnalysisService fraudAnalysisService;

    /**
     * GET /api/graph
     * Returns all nodes and edges for Vis.js network graph rendering.
     *
     * Node status is pulled from the Account.riskLevel field, which uses the
     * same statusFromScore() logic as the accounts table — ensuring consistency.
     */
    @GetMapping("/graph")
    public Map<String, Object> getGraph(
        @RequestParam(defaultValue = "month") String view,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
            if (date == null && !"month".equalsIgnoreCase(view)) {
                date = LocalDate.of(2024, 1, 18);  //default date for testing purposes
            }
        return fraudAnalysisService.getGraphDataForSlice(view, date);
    }

    /**
     * GET /api/fraud/cycles
     * Returns all detected circular money laundering patterns.
     */
    @GetMapping("/fraud/cycles")
    public List<List<String>> getCycles() {
        return fraudAnalysisService.getCycles();
    }

    /**
     * GET /api/fraud/hubs
     * Returns top hub accounts (abnormally high connection counts).
     */
    @GetMapping("/fraud/hubs")
    public List<String> getHubs() {
        return fraudAnalysisService.getHubs();
    }

    /**
     * GET /api/fraud/rapid-hops
     * Returns accounts flagged for rapid transaction bursts within 5 minutes.
     */
    @GetMapping("/fraud/rapid-hops")
    public List<String> getRapidHops() {
        return fraudAnalysisService.getRapidHops();
    }

    /**
     * GET /api/fraud/thresholds
     * Returns accounts flagged for structuring below reporting thresholds.
     */
    @GetMapping("/fraud/thresholds")
    public List<String> getThresholds() {
        return fraudAnalysisService.getThresholds();
    }

    /**
     * GET /api/fraud/propagation
     * Returns BFS-computed risk scores for all accounts (0–100).
     */
    @GetMapping("/fraud/propagation")
    public Map<String, Double> getPropagation() {
        return fraudAnalysisService.getPropagationScores();
    }

    /**
     * GET /api/accounts
     * Returns all 100 accounts with risk scores and status badges.
     */
    @GetMapping("/accounts")
    public List<Map<String, Object>> getAccounts() {
        return fraudAnalysisService.getAccountInfoList();
    }

    /**
     * GET /api/transactions?date=YYYY-MM-DD
     * Returns transactions filtered by date. Without a date param, returns the 100 most recent.
     */
    @GetMapping("/transactions")
    public List<Map<String, Object>> getTransactions(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<Transaction> txns = date != null
                ? fraudAnalysisService.filterByDate(date)
                : fraudAnalysisService.getTransactions().stream()
                    .sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
                    .limit(100)
                    .collect(Collectors.toList());

        return txns.stream().map(this::txnToMap).collect(Collectors.toList());
    }

    /**
     * GET /api/stats
     * Returns summary counts for the dashboard header cards.
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats(
            @RequestParam(defaultValue = "month") String view,
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
            if (date == null && !"month".equalsIgnoreCase(view)) {
                date = LocalDate.of(2024, 1, 18);
            }
        return fraudAnalysisService.getStatsForSlice(view, date);
}

    private Map<String, Object> txnToMap(Transaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("txnId", t.getTxnId());
        m.put("fromAccount", t.getFromAccount());
        m.put("toAccount", t.getToAccount());
        m.put("amount", t.getAmount());
        m.put("timestamp", t.getTimestamp().toString());
        m.put("type", t.getType());
        return m;
    }
}
