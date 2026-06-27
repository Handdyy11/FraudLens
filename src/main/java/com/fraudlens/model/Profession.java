package com.fraudlens.model;

/**
 * Enumeration of professions for realistic account behaviour.
 * Each profession has distinct transaction patterns, income levels, and time preferences.
 * create object of every behavious and sends it 
 */
public enum Profession {
    STUDENT("Student", 0, 2500, 8, 12),
    SALARIED_EMPLOYEE("Salaried Employee", 50000, 100000, 9, 17),
    BUSINESS_OWNER("Business Owner", 80000, 200000, 6, 22),
    FREELANCER("Freelancer", 40000, 150000, 9, 20),
    RETIRED("Retired", 20000, 50000, 8, 18),
    MERCHANT("Merchant", 100000, 500000, 9, 21);

    private final String displayName;
    private final double minMonthlyIncome;
    private final double maxMonthlyIncome;
    private final int preferredHourStart;
    private final int preferredHourEnd;

    Profession(String displayName, double minIncome, double maxIncome, int hourStart, int hourEnd) {
        this.displayName = displayName;
        this.minMonthlyIncome = minIncome;
        this.maxMonthlyIncome = maxIncome;
        this.preferredHourStart = hourStart;
        this.preferredHourEnd = hourEnd;
    }

    public String getDisplayName() { return displayName; }
    public double getMinMonthlyIncome() { return minMonthlyIncome; }
    public double getMaxMonthlyIncome() { return maxMonthlyIncome; }
    public int getPreferredHourStart() { return preferredHourStart; }
    public int getPreferredHourEnd() { return preferredHourEnd; }
}
