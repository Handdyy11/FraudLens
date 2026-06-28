package com.fraudlens.graph;

import com.fraudlens.model.FraudAlert;
import com.fraudlens.patterns.FraudDetector;

import java.util.*;

/**
 * Strategy Pattern implementation — HubDetector is one concrete fraud detection
 * strategy.
 *
 * Algorithm: Max-Heap (PriorityQueue) on combined in+out degree score.
 * Detects accounts with abnormally high connection counts — typical of money
 * mule hubs.
 * Time complexity: O(n log k) where n = accounts, k = TOP_K threshold.
 *
 * Liskov Substitution (SOLID-L): can replace any other FraudDetector without
 * breaking callers.
 * Single Responsibility (SOLID-S): only responsible for hub account detection.
 */
public class HubDetector implements FraudDetector {

    private static final int TOP_K = 10; // report the top 10 hub candidates
    private static final int HUB_THRESHOLD = 6; // minimum degree to be flagged as hub

    @Override
    public List<FraudAlert> detect(TransactionGraph graph) {
        Map<String, Set<String>> adjList = graph.getUniqueAdjacencyList();

        // Build degree map: count unique outgoing + incoming connections per account
        Map<String, Integer> degreeMap = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : adjList.entrySet()) {
            String node = entry.getKey();
            int outDegree = entry.getValue().size();
            int currentDegree = degreeMap.getOrDefault(node, 0);
            degreeMap.put(node, currentDegree + outDegree);
            // Credit in-degree to each destination
            for (String neighbor : entry.getValue()) {
                int degree = degreeMap.getOrDefault(neighbor, 0);
                degreeMap.put(neighbor, degree + 1);
            }
        }

        // Max-Heap: sort all accounts by degree, highest first O(n log k)
        PriorityQueue<Map.Entry<String, Integer>> maxHeap = new PriorityQueue<>((a, b) -> b.getValue() - a.getValue());
        maxHeap.addAll(degreeMap.entrySet());

        // Extract top-K hub accounts that exceed the threshold
        List<String> topHubs = new ArrayList<>();
        int count = 0;
        while (!maxHeap.isEmpty() && count < TOP_K) {
            Map.Entry<String, Integer> entry = maxHeap.poll();
            if (entry.getValue() >= HUB_THRESHOLD) {
                topHubs.add(entry.getKey());
            }
            count++;
        }

        List<FraudAlert> alerts = new ArrayList<>();
        if (!topHubs.isEmpty()) {
            alerts.add(new FraudAlert("HUB",
                    "Accounts with abnormally high connection counts detected (degree ≥ " + HUB_THRESHOLD + ")",
                    topHubs));
        }
        return alerts;
    }
}
