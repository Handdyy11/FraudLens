package com.fraudlens.patterns;

import com.fraudlens.graph.TransactionGraph;
import com.fraudlens.model.FraudAlert;

import java.util.List;

/**
 * Common interface implemented by every fraud detection algorithm.
 *
 * Any new fraud detector must implement the detect() method.
 * This allows FraudAnalysisService to execute all detectors
 * using the same interface (Strategy Pattern).
 */
public interface FraudDetector {
    List<FraudAlert> detect(TransactionGraph graph);
}
