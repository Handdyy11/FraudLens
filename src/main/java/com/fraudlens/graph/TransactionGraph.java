package com.fraudlens.graph;

import com.fraudlens.model.Transaction;
import java.util.*;

/**
 * Builds and maintains the directed adjacency list of UPI transactions.
 */
public class TransactionGraph {

    // Adjacency list: fromAccount -> list of toAccounts
    // Data structure: HashMap<String, List<String>>
    // O(1) average lookup per node
    private HashMap<String, List<String>> adjacencyList;

    // De-duplicated adjacency list (unique edges only)
    private HashMap<String, Set<String>> uniqueEdges;

    // Raw transaction list kept for detectors that need timestamps
    private List<Transaction> transactions;

    public TransactionGraph() {
        this.adjacencyList = new HashMap<>();
        this.uniqueEdges = new HashMap<>();
        this.transactions = new ArrayList<>();
    }

    /**
     * Builds adjacency list from the provided transaction list.
     * Time complexity: O(E) where E = number of transactions
     */
    public void buildGraph(List<Transaction> transactions) {
        this.transactions = new ArrayList<>(transactions);
        this.adjacencyList = new HashMap<>();
        this.uniqueEdges = new HashMap<>();

        for (Transaction t : transactions) {
            String from = t.getFromAccount();
            String to = t.getToAccount();

            // Create sender node if it doesn't already exist.
            if (!adjacencyList.containsKey(from)) {
                adjacencyList.put(from, new ArrayList<>());
            }
            adjacencyList.get(from).add(to);
            // Ensure to node also exists
            if (!adjacencyList.containsKey(to)) {
                adjacencyList.put(to, new ArrayList<>());
            }
            // Unique Edges
            if (!uniqueEdges.containsKey(from)) {
                uniqueEdges.put(from, new HashSet<>());
            }
            uniqueEdges.get(from).add(to);

            if (!uniqueEdges.containsKey(to)) {
                uniqueEdges.put(to, new HashSet<>());
            }
        }
    }

    /** Returns the full adjacency list (may contain duplicate edges). */
    public HashMap<String, List<String>> getAdjacencyList() {
        return adjacencyList;
    }

    /** Returns adjacency list with unique edges only (for cycle/hub detection). */
    public HashMap<String, Set<String>> getUniqueAdjacencyList() {
        return uniqueEdges;
    }

    /** Returns all transactions between two specific accounts. O(E). */
    public List<Transaction> getTransactionsBetween(String from, String to) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (transaction.getFromAccount().equals(from)
                    && transaction.getToAccount().equals(to)) {
                result.add(transaction);
            }
        }
        return result;
    }

    /** Returns the full transaction list for detectors that need timestamps. */
    public List<Transaction> getAllTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    /** Returns all known node IDs. */
    public Set<String> getAllNodes() {
        return adjacencyList.keySet();
    }
}
