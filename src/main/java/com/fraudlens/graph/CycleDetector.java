package com.fraudlens.graph;

import com.fraudlens.model.FraudAlert;
import com.fraudlens.model.Transaction;
import com.fraudlens.patterns.FraudDetector;

import java.util.*;

/**
 * Strategy Pattern implementation — CycleDetector is one concrete fraud
 * detection strategy.
 *
 * Algorithm: Backtracking DFS with a depth limit (max length 10) to find all
 * simple cycles.
 * Detects circular money laundering patterns (e.g. A → B → C → A).
 * Filters out low-value lifestyle transactions using a transaction amount
 * threshold (₹6,000).
 *
 * Liskov Substitution (SOLID-L): can replace any other FraudDetector without
 * breaking callers.
 * Single Responsibility (SOLID-S): only responsible for cycle detection.
 */
public class CycleDetector implements FraudDetector {

    private static final int MAX_CYCLE_LENGTH = 10;
    private static final double MIN_TXN_AMOUNT = 6000.0;

    @Override
    public List<FraudAlert> detect(TransactionGraph graph) {

        Map<String, Set<String>> adjList = new HashMap<>();
        Map<String, Double> maxEdgeAmounts = new HashMap<>();

        // Filters out High value transactions per account
        for (Transaction t : graph.getAllTransactions()) {
            String key = t.getFromAccount() + ">" + t.getToAccount();
            double currentMax = maxEdgeAmounts.getOrDefault(key, 0.0);
            if (t.getAmount() > currentMax) {
                maxEdgeAmounts.put(key, t.getAmount());
            }
        }

        // creates graph of all important edges (amt > max_amount)
        for (Map.Entry<String, Double> entry : maxEdgeAmounts.entrySet()) {
            if (entry.getValue() >= MIN_TXN_AMOUNT) {
                String[] parts = entry.getKey().split(">");
                if (!adjList.containsKey(parts[0])) {
                    adjList.put(parts[0], new HashSet<>());
                }

                adjList.get(parts[0]).add(parts[1]);
            }
        }

        List<List<String>> cycles = new ArrayList<>();
        Set<String> reportedCycles = new HashSet<>(); // avoid duplicates

        for (String startNode : adjList.keySet()) {
            Set<String> visited = new LinkedHashSet<>();
            List<String> path = new ArrayList<>();
            dfs(startNode, startNode, adjList, visited, path, cycles, reportedCycles);
        }

        // Convert each detected cycle path into a FraudAlert
        List<FraudAlert> alerts = new ArrayList<>();
        for (List<String> cycle : cycles) {
            alerts.add(new FraudAlert("CYCLE",
                    "Circular money laundering pattern: " + String.join(" → ", cycle) + " → " + cycle.get(0),
                    new ArrayList<>(cycle)));
        }
        return alerts;
    }

    private void dfs(String startNode,
            String currentNode,
            Map<String, Set<String>> adjList,
            Set<String> visited,
            List<String> path,
            List<List<String>> cycles,
            Set<String> reported) {

        if (path.size() >= MAX_CYCLE_LENGTH)
            return;

        visited.add(currentNode);
        path.add(currentNode);

        Set<String> neighbors = adjList.getOrDefault(currentNode, Collections.emptySet());
        for (String neighbor : neighbors) {
            if (neighbor.equals(startNode)) {
                // Cycle found
                List<String> cycle = new ArrayList<>(path);
                String cycleKey = canonicalKey(cycle); // starting acc_no
                if (!reported.contains(cycleKey)) {
                    reported.add(cycleKey);
                    cycles.add(cycle);
                }
            } else if (!visited.contains(neighbor)) {
                dfs(startNode, neighbor, adjList, visited, path, cycles, reported);
            }
        }
        path.remove(path.size() - 1);
        visited.remove(currentNode);
    }

    /** Produces a rotation-invariant string key for a cycle list. */
    private String canonicalKey(List<String> cycle) {
        int minIdx = 0;
        // gets min account ID from cycle list created
        for (int i = 1; i < cycle.size(); i++) {
            if (cycle.get(i).compareTo(cycle.get(minIdx)) < 0)
                minIdx = i;
        }
        List<String> rotated = new ArrayList<>();
        for (int i = 0; i < cycle.size(); i++) {
            rotated.add(cycle.get((minIdx + i) % cycle.size()));
        }
        return String.join(",", rotated);
    }
}
