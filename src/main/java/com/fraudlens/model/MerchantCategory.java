package com.fraudlens.model;

/**
 * Defines transaction categories used across the fraud analysis model.
 */
public enum MerchantCategory {
    TRANSFER(1000, 10000),
    SHOPPING(300, 2000),
    RESTAURANT(300, 1800),
    TELECOM(100, 800),
    UTILITY(200, 1500),
    FUEL(500, 4000),
    GROCERY(250, 2000),
    ENTERTAINMENT(500, 3000),
    EDUCATION(1000, 8000),
    HOSPITAL(800, 5000),
    RENT(8000, 20000),
    SUBSCRIPTION(100, 1000),
    SALARY(0, 0);

    private final double minAmount;
    private final double maxAmount;

    MerchantCategory(double minAmount, double maxAmount) {
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public double getMinAmount() {
        return minAmount;
    }

    public double getMaxAmount() {
        return maxAmount;
    }
}