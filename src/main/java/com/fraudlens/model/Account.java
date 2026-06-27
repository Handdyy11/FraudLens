package com.fraudlens.model;

public class Account {
    private final String accountId;
    private final String name;
    private final Profession profession;
    private final int age;
    private double currentBalance;
    private final double monthlyIncome;

    // Risk fields — ONLY ever set by FraudAnalysisService, never by the data layer
    private String riskLevel = "UNSCORED";
    private int    riskScore = 0;

    public Account(String accountId, String name, Profession profession, double initialBalance, double monthlyIncome) {
        this(accountId, name, profession, 35, initialBalance, monthlyIncome);
    }

    public Account(String accountId, String name, Profession profession, int age, double initialBalance, double monthlyIncome) {
        this.accountId = accountId;
        this.name      = name;
        this.profession = profession;
        this.age = age;
        this.currentBalance = initialBalance;
        this.monthlyIncome = monthlyIncome;
    }

    // Backward compatibility constructor
    public Account(String accountId, String name) {
        this(accountId, name, Profession.SALARIED_EMPLOYEE, 35, 50000, 50000);
    }

    public String getAccountId() { return accountId; }
    public String getName()      { return name; }
    public Profession getProfession() { return profession; }
    public int getAge() { return age; }
    public double getCurrentBalance() { return currentBalance; }
    public double getMonthlyIncome() { return monthlyIncome; }
    public String getRiskLevel() { return riskLevel; }
    public int    getRiskScore() { return riskScore; }

    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setRiskScore(int riskScore)    { this.riskScore = riskScore; }

    // Balance management
    public void creditBalance(double amount) {
        if (amount > 0) {
            this.currentBalance += amount;
        }
    }

    public void debitBalance(double amount) {
        if (amount > 0 && this.currentBalance >= amount) {
            this.currentBalance -= amount;
        }
    }

    public boolean hassufficientBalance(double amount) {
        return this.currentBalance >= amount;
    }
}
