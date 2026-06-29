package com.fraudlens.service;

import com.fraudlens.data.BehaviourProfiles;
import com.fraudlens.data.BehaviourProfiles.BehaviourProfile;
import com.fraudlens.model.Account;
import com.fraudlens.model.Transaction;

import java.util.*;

public class BehaviourAnalyzer {

    // RuleResult — each rule produces a numeric score AND an explanation
    public record RuleResult(String ruleName, int score, double deviationRatio, String explanation) {
    }

    // ── Rule 1: Transaction Amount Deviation ─────────────────────────────────
    // Deviation ratio at which amount is considered fully abnormal (3× expected
    // max)
    private static final double FULL_DEVIATION_AMOUNT = 3.0;
    private static final int MAX_CONTRIBUTION_AMOUNT = 20;

    // ── Rule 2: Monthly Transaction Frequency Deviation ──────────────────────
    // Deviation ratio at which frequency is considered fully abnormal (3× expected
    // max)
    private static final double FULL_DEVIATION_FREQUENCY = 2.0;
    private static final int MAX_CONTRIBUTION_FREQUENCY = 20;

    // ── Rule 3: Off-Hours Activity ───────────────────────────────────────────
    // Fraction of transactions outside preferred hours considered fully abnormal
    private static final double FULL_DEVIATION_HOURS = 0.50;
    private static final int MAX_CONTRIBUTION_HOURS = 10;

    // Max behaviour score = sum of all rule maximums (20 + 20 + 10)
    private static final int MAX_BEHAVIOUR_SCORE = MAX_CONTRIBUTION_AMOUNT + MAX_CONTRIBUTION_FREQUENCY
            + MAX_CONTRIBUTION_HOURS;

    // Stores last analysis explanations per account (for debugging/logging)
    private Map<String, List<RuleResult>> lastExplanations = new HashMap<>();

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Analyses all accounts and returns a behaviour risk score for each.
     * Score is based on 3 rules: Amount Deviation, Frequency Deviation, Off-Hours
     * Activity.
     *
     * @return map of accountId → behaviour score (0–50)
     */
    public Map<String, Integer> analyse(List<Account> accounts, List<Transaction> transactions) {
        Map<String, List<Transaction>> outgoingByAccount = buildOutgoingMap(transactions);
        Map<String, List<Transaction>> incomingByAccount = buildIncomingMap(transactions);
        Map<String, Integer> behaviourScores = new HashMap<>();
        Map<String, List<RuleResult>> explanations = new HashMap<>();

        for (Account account : accounts) {
            String id = account.getAccountId();
            BehaviourProfile profile = BehaviourProfiles.forProfession(account.getProfession());
            List<Transaction> outgoing = outgoingByAccount.getOrDefault(id, Collections.emptyList());
            List<Transaction> incoming = incomingByAccount.getOrDefault(id, Collections.emptyList());
            String professionName = profile.profession().getDisplayName();

            // Evaluate 3 rules — each returns a RuleResult with score + explanation
            List<RuleResult> results = new ArrayList<>();
            results.add(scoreAmountDeviation(outgoing, incoming, profile, professionName));
            results.add(scoreFrequencyDeviation(outgoing, profile, professionName));
            results.add(scoreOffHoursActivity(outgoing, profile, professionName));

            int totalScore = results.stream().mapToInt(RuleResult::score).sum();
            totalScore = Math.min(Math.max(totalScore, 0), MAX_BEHAVIOUR_SCORE);

            behaviourScores.put(id, totalScore);
            explanations.put(id, results);
        }

        this.lastExplanations = explanations;
        return behaviourScores;
    }

    public Map<String, List<RuleResult>> getLastExplanations() {
        return Collections.unmodifiableMap(lastExplanations);
    }

    // ── Universal scoring formula ─────────────────────────────────────────────

    /**
     * Converts a deviation ratio into a bounded score using linear interpolation.
     * score = min(maxContribution, round(deviation / fullDeviation ×
     * maxContribution))
     */
    private static int computeRuleScore(double deviation, double fullDeviation, int maxContribution) {
        if (deviation <= 0)
            return 0;
        int score = (int) Math.round(deviation / fullDeviation * maxContribution);
        return Math.min(score, maxContribution);
    }

    // ── Rule 1: Amount Deviation (Primary, max 20) ───────────────────────────

    /**
     * Compares the largest actual transaction (incoming or outgoing) against the
     * profile's expected maximum amount.
     *
     * deviation = (actualMax - expectedMax) / expectedMax (only when actualMax >
     * expected)
     *
     * Example: Expected max ₹2,500 — actual max ₹10,000 → deviation = 3.0 → score =
     * 20
     */
    private RuleResult scoreAmountDeviation(List<Transaction> outgoing, List<Transaction> incoming,
            BehaviourProfile profile, String profession) {
        if (outgoing.isEmpty() && incoming.isEmpty()) {
            return new RuleResult("AMOUNT_DEVIATION", 0, 0.0, "No transactions");
        }

        double maxExpected = profile.maxTxnAmount();
        double maxOutgoing = outgoing.stream().mapToDouble(Transaction::getAmount).max().orElse(0.0);
        double maxIncoming = incoming.stream().mapToDouble(Transaction::getAmount).max().orElse(0.0);
        double actualMax = Math.max(maxOutgoing, maxIncoming);

        double deviation = 0.0;
        if (maxExpected > 0 && actualMax > maxExpected) {
            deviation = (actualMax - maxExpected) / maxExpected;
        }

        int score = computeRuleScore(deviation, FULL_DEVIATION_AMOUNT, MAX_CONTRIBUTION_AMOUNT);

        String explanation = String.format(
                "Unusually large transaction: A ₹%.0f transfer was detected, which is %.1f× above the typical maximum (₹%.0f) for a %s profile. This may indicate account takeover or money laundering.",
                actualMax, deviation, maxExpected, profession);

        return new RuleResult("AMOUNT_DEVIATION", score, deviation, explanation);
    }

    // ── Rule 2: Frequency Deviation (Primary, max 20) ────────────────────────

    /**
     * Compares actual outgoing transaction count against the profile's expected
     * maximum monthly count. Only penalises excess — low activity is not
     * suspicious.
     *
     * deviation = (actualCount - expectedMax) / expectedMax (only when actualCount
     * > expected)
     *
     * Example: Expected max 15 txns — actual 45 → deviation = 2.0 → score = 20
     */
    private RuleResult scoreFrequencyDeviation(List<Transaction> outgoing,
            BehaviourProfile profile, String profession) {
        int actualCount = outgoing.size();
        int maxExpected = profile.maxMonthlyTxns();

        if (maxExpected <= 0) {
            return new RuleResult("FREQUENCY_DEVIATION", 0, 0.0, "No expected frequency defined");
        }

        double deviation = 0.0;
        if (actualCount > maxExpected) {
            deviation = (double) (actualCount - maxExpected) / maxExpected;
        }

        int score = computeRuleScore(deviation, FULL_DEVIATION_FREQUENCY, MAX_CONTRIBUTION_FREQUENCY);

        String explanation = String.format(
                "Abnormal transaction volume: Detected %d outgoing transactions this month, exceeding the expected limit of %d for a %s profile. High frequency is often associated with automated scripts or mule account activity.",
                actualCount, maxExpected, profession);

        return new RuleResult("FREQUENCY_DEVIATION", score, deviation, explanation);
    }

    // ── Rule 3: Off-Hours Activity (Secondary, max 10) ───────────────────────

    /**
     * Measures the fraction of outgoing transactions outside the account's
     * preferred active hours. The fraction itself is the deviation.
     *
     * Example: Salaried employee (9AM–5PM) — 50% of txns outside hours → score = 10
     */
    private RuleResult scoreOffHoursActivity(List<Transaction> outgoing,
            BehaviourProfile profile, String profession) {
        if (outgoing.isEmpty()) {
            return new RuleResult("OFF_HOURS_ACTIVITY", 0, 0.0, "No outgoing transactions");
        }

        int hourStart = profile.profession().getPreferredHourStart();
        int hourEnd = profile.profession().getPreferredHourEnd();

        long outsideCount = outgoing.stream()
                .filter(t -> {
                    int hour = t.getTimestamp().getHour();
                    return hour < hourStart || hour >= hourEnd;
                })
                .count();

        double deviation = (double) outsideCount / outgoing.size();

        int score = computeRuleScore(deviation, FULL_DEVIATION_HOURS, MAX_CONTRIBUTION_HOURS);

        String explanation = String.format(
                "Suspicious timing: %.0f%% of transactions occurred during odd hours (outside %dAM–%dPM). Fraudsters frequently operate during off-hours to avoid immediate detection by victims.",
                deviation * 100, hourStart, hourEnd);

        return new RuleResult("OFF_HOURS_ACTIVITY", score, deviation, explanation);
    }

    // ── Utility methods ───────────────────────────────────────────────────────

    private Map<String, List<Transaction>> buildOutgoingMap(List<Transaction> transactions) {
        Map<String, List<Transaction>> map = new HashMap<>();
        for (Transaction t : transactions) {
            map.computeIfAbsent(t.getFromAccount(), k -> new ArrayList<>()).add(t);
        }
        return map;
    }

    private Map<String, List<Transaction>> buildIncomingMap(List<Transaction> transactions) {
        Map<String, List<Transaction>> map = new HashMap<>();
        for (Transaction t : transactions) {
            map.computeIfAbsent(t.getToAccount(), k -> new ArrayList<>()).add(t);
        }
        return map;
    }
}