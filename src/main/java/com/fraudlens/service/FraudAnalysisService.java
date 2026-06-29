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
 */
@Service
public class FraudAnalysisService {

    private final TransactionGraph graph = new TransactionGraph();

    private final List<FraudDetector> detectors = Arrays.asList(
            new CycleDetector(),
            new HubDetector(),
            new RapidHopDetector(),
            new ThresholdDetector());

    private final PropagationScorer propagationScorer = new PropagationScorer();
    private final BehaviourAnalyzer behaviourAnalyzer = new BehaviourAnalyzer();

    private List<Account> accounts;
    private List<Transaction> transactions;

    private List<FraudAlert> lastAlerts;
    private Map<String, Double> propagationScores;
    private Map<String, Integer> behaviourScores;
    private Map<String, Integer> patternScores;

    public void initialize(List<Account> accounts, List<Transaction> transactions) {
        this.accounts = new ArrayList<>(accounts);
        this.transactions = new ArrayList<>(transactions);
        rebuildAndDetect();
    }



    private void rebuildAndDetect() {
        graph.buildGraph(transactions);

        lastAlerts = new ArrayList<>();
        for (FraudDetector detector : detectors) {
            lastAlerts.addAll(detector.detect(graph));
        }

        Set<String> fraudAccounts = getFraudAccountIds();
        propagationScores = propagationScorer.computeScores(graph, fraudAccounts);
        behaviourScores = behaviourAnalyzer.analyse(accounts, transactions);
        patternScores = computePatternScores();

        stampRiskOnAccounts();
    }

    private void stampRiskOnAccounts() {
        for (Account acc : accounts) {
            String id = acc.getAccountId();
            int score = computeFinalRiskScore(id);
            acc.setRiskScore(score);
            acc.setRiskLevel(statusFromScore(score));
        }
    }

    // ── Pattern score constants ───────────────────────────────────────────────
    //
    // These scores feed into computePatternScores(). The weighted formula
    // is applied for RAPID_HOP, THRESHOLD, and HUB accounts.
    // CYCLE accounts bypass the formula entirely — see computeFinalRiskScore().
    //
    // | Pattern | Severity | Confidence | Score |
    // |------------|----------|------------|-------|
    // | CYCLE | CRITICAL | HIGH | 100 | bypasses formula → always FRAUD |
    // | RAPID_HOP | CRITICAL | MODERATE | 60 | 60×0.60 = 36, +behaviour →
    // SUSPICIOUS |
    // | THRESHOLD | HIGH | MODERATE | 50 | 50×0.60 = 30, +behaviour → SUSPICIOUS |
    // | HUB | HIGH | LOW | 40 | 40×0.60 = 24, secondary signal |

    private static final int CYCLE_SCORE = 100;
    private static final int RAPID_HOP_SCORE = 50;
    private static final int THRESHOLD_SCORE = 50;
    private static final int HUB_SCORE = 50;
    private static final int DEFAULT_ALERT_SCORE = 40;

    // ── Base + Bonus Additive Model ──────────────────────────────────────────
    //
    // Final Risk = Base Pattern Score + (Propagation Bonus) + (Behaviour Bonus)
    //
    // Why this model:
    // - Pattern (Base): Direct fraud evidence. A strong pattern (like 50) instantly 
    //   pushes an account into SUSPICIOUS territory on its own.
    // - Propagation (Bonus): Proximity to fraud nodes adds up to 25 points.
    // - Behaviour (Bonus): Abnormal behaviour adds up to 14 points of supporting evidence.
    //
    // Result tiers:
    // - Fraud pattern account → pattern score 50 → final 50-89 (SUSPICIOUS/FRAUD)
    // - Multiple pattern account → pattern score 100 → final 100 (FRAUD)
    // - Linked account → propagation up to ~25 → final ~25 (AT_RISK)
    // - Innocent account → all zeros → final 0

    private static final double WEIGHT_PROPAGATION = 0.25;
    private static final double WEIGHT_BEHAVIOUR = 0.14;

    // BehaviourAnalyzer max score is 100 (40+35+25). Used for normalisation.
    private static final double MAX_BEHAVIOUR_RAW = 100.0;

    private Map<String, Integer> computePatternScores() {
        Map<String, Integer> scores = new HashMap<>();
        for (FraudAlert alert : lastAlerts) {
            int contribution = switch (alert.getType()) {
                case "CYCLE" -> CYCLE_SCORE;
                case "RAPID_HOP" -> RAPID_HOP_SCORE;
                case "THRESHOLD" -> THRESHOLD_SCORE;
                case "HUB" -> HUB_SCORE;
                default -> DEFAULT_ALERT_SCORE;
            };
            for (String accountId : alert.getInvolvedAccounts()) {
                scores.merge(accountId, contribution, Integer::sum);
            }
        }
        scores.replaceAll((k, v) -> Math.min(v, 100));
        return scores;
    }

    /**
     * Computes the final risk score for one account.
     *
     * CYCLE accounts are short-circuited to 100 (always FRAUD).
     * Circular money flow (A→B→C→A) is textbook money laundering.
     *
     * All other accounts use the Base + Bonus formula:
     * Final Risk = patternScore
     * + (propagationScore × 0.50)
     * + (normalisedBehaviourScore × 0.20)
     *
     * Behaviour is normalised from 0–100 → 0–100 before applying weight.
     * Result clamped to [0, 100].
     */
    private int computeFinalRiskScore(String accountId) {
        // CYCLE = confirmed fraud. Bypass the formula entirely.
        boolean inCycle = lastAlerts.stream()
                .anyMatch(a -> a.getType().equals("CYCLE")
                        && a.getInvolvedAccounts().contains(accountId));
        if (inCycle)
            return 100;

        double pattern = patternScores.getOrDefault(accountId, 0);
        double propagation = propagationScores.getOrDefault(accountId, 0.0);

        double behaviourRaw = behaviourScores.getOrDefault(accountId, 0);
        double behaviourNormalised = (behaviourRaw / MAX_BEHAVIOUR_RAW) * 100.0;

        double finalScore = pattern 
                + (propagation * WEIGHT_PROPAGATION)
                + (behaviourNormalised * WEIGHT_BEHAVIOUR);

        return Math.min((int) Math.round(finalScore), 100);
    }

    /**
     * Maps composite score to risk level label.
     *
     * FRAUD ≥ 80 — strong multi-detector evidence
     * SUSPICIOUS ≥ 40 — at least one detector flagged this account
     * AT_RISK ≥ 15 — linked to a fraud account via propagation
     * NORMAL < 15 — no evidence of fraud
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

    public List<Map<String, Object>> getAccountInfoList() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Long> txnCount = buildTxnCountMap();
        for (Account acc : accounts) {
            String id = acc.getAccountId();
            long alertCount = lastAlerts.stream()
                    .filter(a -> a.getInvolvedAccounts().contains(id))
                    .count();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("accountId", id);
            info.put("name", acc.getName());
            info.put("totalTransactions", txnCount.getOrDefault(id, 0L));
            info.put("riskScore", acc.getRiskScore());
            info.put("status", acc.getRiskLevel());
            info.put("alertCount", alertCount);
            result.add(info);
        }
        return result;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Map<String, Long> buildTxnCountMap() {
        Map<String, Long> map = new HashMap<>();
        for (Transaction t : transactions) {
            map.merge(t.getFromAccount(), 1L, Long::sum);
            map.merge(t.getToAccount(), 1L, Long::sum);
        }
        return map;
    }

    private void ensureAccountExists(String id) {
        boolean exists = accounts.stream().anyMatch(a -> a.getAccountId().equals(id));
        if (!exists) {
            accounts.add(new Account(id, "User " + id));
        }
    }

    // ── Public accessors ──────────────────────────────────────────────────────

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
                .distinct().collect(Collectors.toList());
    }

    public List<String> getRapidHops() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("RAPID_HOP"))
                .flatMap(a -> a.getInvolvedAccounts().stream())
                .distinct().collect(Collectors.toList());
    }

    public List<String> getThresholds() {
        return lastAlerts.stream()
                .filter(a -> a.getType().equals("THRESHOLD"))
                .flatMap(a -> a.getInvolvedAccounts().stream())
                .distinct().collect(Collectors.toList());
    }

    public Set<String> getFraudAccountIds() {
        Set<String> fraud = new HashSet<>();
        for (FraudAlert alert : lastAlerts) {
            fraud.addAll(alert.getInvolvedAccounts());
        }
        return fraud;
    }

    public Map<String, Object> getGraphDataForSlice(String view, LocalDate date) {
        List<Transaction> slice = getTransactionsForSlice(view, date);

        Set<String> activeNodes = new HashSet<>();
        for (Transaction t : slice) {
            activeNodes.add(t.getFromAccount());
            activeNodes.add(t.getToAccount());
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (String nodeId : activeNodes) {
            Account acc = findAccountById(nodeId);
            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put("id", nodeId);
            nodeMap.put("label", nodeId);
            nodeMap.put("riskScore", (acc != null) ? acc.getRiskScore() : 0);
            nodeMap.put("status", (acc != null) ? acc.getRiskLevel() : "NORMAL");
            nodes.add(nodeMap);
        }

        Map<String, Map<String, Object>> edgeMap = new LinkedHashMap<>();
        for (Transaction txn : slice) {
            String key = txn.getFromAccount() + ">" + txn.getToAccount();
            edgeMap.computeIfAbsent(key, k -> {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("from", txn.getFromAccount());
                edge.put("to", txn.getToAccount());
                edge.put("totalAmount", 0.0);
                edge.put("txnCount", 0);
                edge.put("type", "NORMAL");
                return edge;
            });
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

    public List<Transaction> getTransactionsForSlice(String view, LocalDate date) {
        if (date == null || "month".equals(view))
            return new ArrayList<>(transactions);

        List<Transaction> result = new ArrayList<>();
        if ("day".equals(view)) {
            for (Transaction t : transactions) {
                if (t.getTimestamp().toLocalDate().equals(date))
                    result.add(t);
            }
        } else if ("week".equals(view)) {
            LocalDate monday = date
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            LocalDate sunday = date.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
            for (Transaction t : transactions) {
                LocalDate tDate = t.getTimestamp().toLocalDate();
                if (!tDate.isBefore(monday) && !tDate.isAfter(sunday))
                    result.add(t);
            }
        }
        return result;
    }

    public Map<String, Object> getStatsForSlice(String view, LocalDate date) {
        List<Transaction> slice = getTransactionsForSlice(view, date);

        Set<String> activeNodes = new HashSet<>();
        Set<String> edgeKeys = new HashSet<>();
        for (Transaction t : slice) {
            activeNodes.add(t.getFromAccount());
            activeNodes.add(t.getToAccount());
            edgeKeys.add(t.getFromAccount() + ">" + t.getToAccount());
        }

        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("FRAUD", 0L);
        distribution.put("SUSPICIOUS", 0L);
        distribution.put("AT_RISK", 0L);
        distribution.put("NORMAL", 0L);
        for (Account acc : accounts) {
            distribution.merge(acc.getRiskLevel(), 1L, Long::sum);
        }

        int cycles = 0, hubs = 0, rapids = 0;
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
        stats.put("totalAccounts", accounts.size());
        stats.put("edgeCount", edgeKeys.size());
        stats.put("cyclesDetected", cycles);
        stats.put("hubAccounts", hubs);
        stats.put("rapidHopAlerts", rapids);
        stats.put("fraudAlerts", lastAlerts.size());
        stats.put("riskDistribution", distribution);
        return stats;
    }

    private Account findAccountById(String accountId) {
        for (Account acc : accounts) {
            if (acc.getAccountId().equals(accountId))
                return acc;
        }
        return null;
    }
}