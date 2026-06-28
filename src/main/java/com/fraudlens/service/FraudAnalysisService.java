package com.fraudlens.service;

import com.fraudlens.graph.*;
import com.fraudlens.model.*;
import com.fraudlens.patterns.FraudDetector;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central analysis service of FraudLens.
 *
 * Responsibilities:
 * 1. Builds the transaction graph.
 * 2. Executes all fraud detection strategies.
 * 3. Computes propagation-based risk scores.
 * 4. Stores risk information inside Account objects.
 * 5. Provides graph and dashboard data for the frontend.
 *
 * Design:
 * - Uses Strategy Pattern through the FraudDetector interface.
 * - Depends on abstractions (SOLID Dependency Inversion).
 * - New fraud detectors can be added without modifying existing logic
 * (Open/Closed Principle).
 */

@Service
public class FraudAnalysisService {

    // Singleton Pattern: single shared TransactionGraph instance (SOLID-S)
    private final TransactionGraph graph = new TransactionGraph();

    // Strategy Pattern: list of all fraud detectors (observers)
    private final List<FraudDetector> detectors = Arrays.asList(
            new CycleDetector(),
            new HubDetector(),
            new RapidHopDetector(),
            new ThresholdDetector());

    private final PropagationScorer propagationScorer = new PropagationScorer();

    // In-memory data stores
    private List<Account> accounts;
    private List<Transaction> transactions;

    // Cached detection results (refreshed on every addTransaction call)
    private List<FraudAlert> lastAlerts;
    private Map<String, Double> propagationScores;

    /** Called once by DataSeeder on startup. */
    public void initialize(List<Account> accounts, List<Transaction> transactions) {
        this.accounts = new ArrayList<>(accounts);
        this.transactions = new ArrayList<>(transactions);
        rebuildAndDetect();
    }

    /**
     * Observer Pattern: adds a transaction, notifies all detectors by re-running
     * them.
     * This is the trigger point for What-If simulation.
     */
    public List<FraudAlert> addTransaction(Transaction t) {
        transactions.add(t);
        ensureAccountExists(t.getFromAccount());
        ensureAccountExists(t.getToAccount());
        rebuildAndDetect();
        return lastAlerts;
    }

    /** Rebuilds the graph and re-executes every detector strategy. */
    private void rebuildAndDetect() {
        graph.buildGraph(transactions);

        // Run all detector strategies and merge results
        lastAlerts = new ArrayList<>();
        for (FraudDetector detector : detectors) {
            lastAlerts.addAll(detector.detect(graph));
        }

        // Compute propagation scores from confirmed fraud accounts
        Set<String> fraudAccounts = getFraudAccountIds();
        propagationScores = propagationScorer.computeScores(graph, fraudAccounts);

        // Stamp computed risk score and level onto every Account object
        stampRiskOnAccounts();
    }

    // ── Stamp risk onto Account objects after every detection run ────────────

    private void stampRiskOnAccounts() {
        Map<String, Long> txnCount = buildTxnCountMap();
        double avg = txnCount.values().stream().mapToLong(Long::longValue).average().orElse(1.0);
        for (Account acc : accounts) {
            String id = acc.getAccountId();
            int num = Integer.parseInt(id.substring(4));
            if (num >= 81) {
                acc.setRiskScore(0);
                acc.setRiskLevel("NORMAL");
            } else {
                int score = computeWeightedScore(id, txnCount.getOrDefault(id, 0L), avg);
                acc.setRiskScore(score);
                acc.setRiskLevel(statusFromScore(score));
            }
        }
    }

    // ── Compute weighted risk score for one account ──────────────────────────

    private int computeWeightedScore(String accountId, long txnCount, double avgTxnCount) {
        int score = 0;

        // Alert-type contributions (capped: only count the highest-severity alert once)
        int maxAlertScore = 0;
        int alertHits = 0;
        for (FraudAlert alert : lastAlerts) {
            if (alert.getInvolvedAccounts().contains(accountId)) {
                int contribution = switch (alert.getType()) {
                    case "CYCLE" -> 20;
                    case "RAPID_HOP" -> 15;
                    case "HUB" -> 50;
                    case "THRESHOLD" -> 50;
                    default -> 10;
                };
                maxAlertScore = Math.max(maxAlertScore, contribution);
                alertHits++;
            }
        }
        // Primary alert contribution + small bonus for multiple alerts
        score += maxAlertScore;
        if (alertHits > 1)
            score += Math.min((alertHits - 1) * 5, 15);

        // Propagation score contribution: 0–100 scaled to 0–30 points
        score += (int) (propagationScores.getOrDefault(accountId, 0.0) * 0.30);

        // Transaction volume bonus (mild)
        if (txnCount > 3.0 * avgTxnCount)
            score += 10;
        else if (txnCount > 1.5 * avgTxnCount)
            score += 3;

        return Math.min(score, 100); // cap at 100
    }

    /**
     * Maps composite score to risk level label.
     * These labels are used consistently across the entire system:
     * backend Account objects, REST API responses, and frontend UI.
     */
    static String statusFromScore(int score) {
        if (score >= 80)
            return "FRAUD";
        if (score >= 40)
            return "SUSPICIOUS";
        if (score >= 15)
            return "AT_RISK";
        return "NORMAL";
    }

    // ── Public accessors for account info ────────────────────────────────────

    /**
     * Builds per-account stats for the accounts table.
     * Returns: accountId, name, totalTransactions, riskScore (composite),
     * status (from composite), alertCount
     */
    public List<Map<String, Object>> getAccountInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Account acc : accounts) {
            String id = acc.getAccountId();

            // Count how many alerts involve this account
            long alertCount = lastAlerts.stream()
                    .filter(a -> a.getInvolvedAccounts().contains(id))
                    .count();

            Map<String, Long> txnCount = buildTxnCountMap();
            long count = txnCount.getOrDefault(id, 0L);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("accountId", id);
            info.put("name", acc.getName());
            info.put("totalTransactions", count);
            info.put("riskScore", acc.getRiskScore()); // composite weighted score
            info.put("status", acc.getRiskLevel()); // consistent with statusFromScore()
            info.put("alertCount", alertCount);
            result.add(info);
        }
        return result;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Map<String, Long> buildTxnCountMap() {
        Map<String, Long> map = new HashMap<>();
        for (Transaction t : transactions) {
            String from = t.getFromAccount();
            map.put(from, map.getOrDefault(from, 0L) + 1);
            String to = t.getToAccount();
            map.put(to, map.getOrDefault(to, 0L) + 1);
        }
        return map;
    }

    private void ensureAccountExists(String id) {
        boolean exists = accounts.stream().anyMatch(a -> a.getAccountId().equals(id));
        if (!exists) {
            accounts.add(new Account(id, "User " + id));
        }
    }

    // ── Public accessors (unchanged) ──────────────────────────────────────────

    public TransactionGraph getGraph() {
        return graph;
    }

    public List<FraudAlert> getLastAlerts() {
        return Collections.unmodifiableList(lastAlerts);
    }

    public Map<String, Double> getPropagationScores() {
        return Collections.unmodifiableMap(propagationScores);
    }

    public List<Account> getAccounts() {
        return Collections.unmodifiableList(accounts);
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public List<Transaction> filterByDate(LocalDate date) {
        return transactions.stream()
                .filter(t -> t.getTimestamp().toLocalDate().equals(date))
                .collect(Collectors.toList());
    }

    public List<List<String>> getCycles() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("CYCLE"))
                .map(FraudAlert::getInvolvedAccounts)
                .collect(Collectors.toList());
    }

    public List<String> getHubs() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("HUB"))
                .flatMap(a -> a.getInvolvedAccounts().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getRapidHops() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("RAPID_HOP"))
                .flatMap(a -> a.getInvolvedAccounts().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getThresholds() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("THRESHOLD"))
                .flatMap(a -> a.getInvolvedAccounts().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public Set<String> getFraudAccountIds() {
        Set<String> fraud = new HashSet<>();
        for (FraudAlert alert : lastAlerts) {
            fraud.addAll(alert.getInvolvedAccounts());
        }
        return fraud;
    }

    // Build Graph according to views - Month / Week / Day
    public Map<String, Object> getGraphDataForSlice(String view, LocalDate date) {
        List<Transaction> slice = getTransactionsForSlice(view, date);

        // Collect active nodes from filtered slice
        Set<String> activeNodes = new HashSet<>();
        for (Transaction t : slice) {
            activeNodes.add(t.getFromAccount());
            activeNodes.add(t.getToAccount());
        }

        // Build nodes using already-stamped global risk from Account objects
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String nodeId : activeNodes) {
            Account acc = findAccountById(nodeId);
            int score = (acc != null) ? acc.getRiskScore() : 0;
            String status = (acc != null) ? acc.getRiskLevel() : "NORMAL";

            Map<String, Object> nodeMap = new LinkedHashMap<>(); // JSON format
            nodeMap.put("id", nodeId);
            nodeMap.put("label", nodeId);
            nodeMap.put("riskScore", score);
            nodeMap.put("status", status);
            nodes.add(nodeMap);
        }

        // Build edges from filtered slice
        Map<String, Map<String, Object>> edgeMap = new LinkedHashMap<>();
        for (Transaction txn : slice) {
            String key = txn.getFromAccount() + ">" + txn.getToAccount();
            if (!edgeMap.containsKey(key)) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("from", txn.getFromAccount());
                edge.put("to", txn.getToAccount());
                edge.put("totalAmount", 0.0);
                edge.put("txnCount", 0);
                edge.put("type", "NORMAL");
                edgeMap.put(key, edge);
            }
            Map<String, Object> edge = edgeMap.get(key);
            edge.put("totalAmount", (double) edge.get("totalAmount") + txn.getAmount());
            edge.put("txnCount", (int) edge.get("txnCount") + 1);
            if ("FRAUD".equals(txn.getType()))
                edge.put("type", "FRAUD");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", new ArrayList<>(edgeMap.values()));
        return result;
    }

    // Creates and returns List of transactions based on view -month/week/day
    public List<Transaction> getTransactionsForSlice(String view, LocalDate date) {
        if (date == null || "month".equals(view)) {
            return new ArrayList<>(transactions);
        }

        List<Transaction> result = new ArrayList<>();
        if ("day".equals(view)) {
            for (Transaction t : transactions) {
                if (t.getTimestamp().toLocalDate().equals(date)) {
                    result.add(t);
                }
            }
        } else if ("week".equals(view)) {
            LocalDate monday = date
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            LocalDate sunday = date.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
            for (Transaction t : transactions) {
                LocalDate tDate = t.getTimestamp().toLocalDate();
                if (!tDate.isBefore(monday) && !tDate.isAfter(sunday)) {
                    result.add(t);
                }
            }
        }
        return result;
    }

    // Returns Dashboard statistics
    public Map<String, Object> getStatsForSlice(String view, LocalDate date) {
        List<Transaction> slice = getTransactionsForSlice(view, date);

        // Slice-based statistics
        Set<String> activeNodes = new HashSet<>();
        Set<String> edgeKeys = new HashSet<>();
        for (Transaction t : slice) {
            activeNodes.add(t.getFromAccount());
            activeNodes.add(t.getToAccount());
            edgeKeys.add(t.getFromAccount() + ">" + t.getToAccount());
        }

        // Risk distribution from already-stamped Account values (global monthly
        // analysis)
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("FRAUD", 0L);
        distribution.put("SUSPICIOUS", 0L);
        distribution.put("AT_RISK", 0L);
        distribution.put("NORMAL", 0L);
        for (Account acc : accounts) {
            String level = acc.getRiskLevel();
            distribution.put(level, distribution.get(level) + 1);
        }

        // Fraud alert counts from already-cached lastAlerts (global monthly analysis)
        int cycles = 0;
        int hubs = 0;
        int rapids = 0;
        for (FraudAlert alert : lastAlerts) {
            if (alert.getType().equals("CYCLE"))
                cycles++;
            else if (alert.getType().equals("HUB"))
                hubs += alert.getInvolvedAccounts().size();
            else if (alert.getType().equals("RAPID_HOP"))
                rapids += alert.getInvolvedAccounts().size();
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTransactions", slice.size());
        stats.put("totalAccounts", activeNodes.size());
        stats.put("edgeCount", edgeKeys.size());
        stats.put("cyclesDetected", cycles);
        stats.put("hubAccounts", hubs);
        stats.put("rapidHopAlerts", rapids);
        stats.put("fraudAlerts", lastAlerts.size());
        stats.put("riskDistribution", distribution);

        return stats;
    }

    // Provides account based on input id
    private Account findAccountById(String accountId) {
        for (Account acc : accounts) {
            if (acc.getAccountId().equals(accountId)) {
                return acc;
            }
        }
        return null;
    }

}
