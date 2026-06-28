package com.fraudlens.data;

import com.fraudlens.model.Account;
import com.fraudlens.model.MerchantCategory;
import com.fraudlens.model.Transaction;
import com.fraudlens.service.FraudAnalysisService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Orchestrates startup data seeding with realistic transaction logs.
 *
 * Flow:
 * 1. Create 100 accounts with profession-based profiles and initial balances
 * 2. Generate salary transactions for salaried employees
 * 3. Generate realistic lifestyle transactions (merchant + peer-to-peer)
 * 4. Inject fraud patterns (maintaining account balances)
 * 5. Hand off to FraudAnalysisService
 */

@Component
public class DataSeeder {

    @Autowired
    private FraudAnalysisService fraudAnalysisService;

    @PostConstruct
    public void init() {
        // Step 1: Create 100 accounts with professions and balances
        List<Account> accounts = new AccountFactory().createAccounts();

        List<Transaction> allTransactions = new ArrayList<>();

        // Step 2: Generate salary transactions
        SalaryGenerator salaryGen = new SalaryGenerator();
        Random salaryRnd = new Random(11111L);
        allTransactions.addAll(salaryGen.generateSalaries(accounts, salaryRnd));

        // Step 3: Generate normal lifestyle transactions
        LifestyleGenerator lifestyleGen = new LifestyleGenerator(33333L);
        for (Account acc : accounts) {
            int accountNum = Integer.parseInt(acc.getAccountId().substring(4));
            if (accountNum <= 80) { // Only generate for non-merchant accounts
                allTransactions.addAll(lifestyleGen.generateFor(acc, accounts));
            }
        }

        // Step 4: Pick fraud participant accounts (seeded and non-consecutive)
        Random fraudRnd = new Random(12345L);
        Set<String> usedAccounts = new HashSet<>();

        String cycle1_1 = pickRandomAccount(fraudRnd, usedAccounts);
        String cycle1_2 = pickRandomAccount(fraudRnd, usedAccounts);
        String cycle1_3 = pickRandomAccount(fraudRnd, usedAccounts);

        String hub1_center = pickRandomAccount(fraudRnd, usedAccounts);

        String rh_1 = pickRandomAccount(fraudRnd, usedAccounts);
        String rh_2 = pickRandomAccount(fraudRnd, usedAccounts);
        String rh_3 = pickRandomAccount(fraudRnd, usedAccounts);
        String rh_4 = pickRandomAccount(fraudRnd, usedAccounts);

        String th_1 = pickRandomAccount(fraudRnd, usedAccounts);

        // Print selected fraud seeds for verification
        System.out.println("[FraudLens Seeding] Selected Fraud Seeds:");
        System.out.println("  Cycle 1: " + cycle1_1 + ", " + cycle1_2 + ", " + cycle1_3);
        System.out.println("  Hub 1 Center: " + hub1_center);
        System.out.println("  Rapid Hop: " + rh_1 + " -> " + rh_2 + " -> " + rh_3 + " -> " + rh_4);
        System.out.println("  Threshold Sender: " + th_1);

        // Step 6: Inject fraud patterns

        // 1. Cycle Pattern: C1_1 -> C1_2 -> C1_3 -> C1_1 (Spans multiple days/weeks)
        double c1Amt = 12500.0;
        allTransactions.add(new Transaction(uid("CYC", fraudRnd), cycle1_1, cycle1_2, c1Amt,
                LocalDateTime.of(2024, 1, 4, 10, 15), "NORMAL", MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("CYC", fraudRnd), cycle1_2, cycle1_3, c1Amt,
                LocalDateTime.of(2024, 1, 10, 14, 30), "NORMAL", MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("CYC", fraudRnd), cycle1_3, cycle1_1, c1Amt,
                LocalDateTime.of(2024, 1, 15, 11, 45), "NORMAL", MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("CYC", fraudRnd), cycle1_1, cycle1_2, c1Amt,
                LocalDateTime.of(2024, 1, 19, 9, 30), "NORMAL", MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("CYC", fraudRnd), cycle1_2, cycle1_3, c1Amt,
                LocalDateTime.of(2024, 1, 23, 16, 10), "NORMAL", MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("CYC", fraudRnd), cycle1_3, cycle1_1, c1Amt,
                LocalDateTime.of(2024, 1, 28, 12, 0), "NORMAL", MerchantCategory.TRANSFER));
        updateAccountBalances(accounts, cycle1_1, cycle1_2, cycle1_3, c1Amt); // Update balances for cycle

        // 2. Hub Pattern: Hub center sends to 6 random leaves
        double h1Amt = 8500.0;
        List<String> h1Leaves = new ArrayList<>(); // stores all recievers account_ids
        for (int i = 0; i < 6; i++) {
            h1Leaves.add(pickRandomAccount(fraudRnd, usedAccounts));
        }
        for (int i = 0; i < h1Leaves.size(); i++) {
            String leaf = h1Leaves.get(i);
            allTransactions.add(new Transaction(uid("HUB", fraudRnd), hub1_center, leaf, h1Amt,
                    LocalDateTime.of(2024, 1, 3 + i * 4, 10, 0), "NORMAL", MerchantCategory.TRANSFER));
            allTransactions.add(new Transaction(uid("HUB", fraudRnd), hub1_center, leaf, h1Amt,
                    LocalDateTime.of(2024, 1, 15 + i * 2, 14, 0), "NORMAL", MerchantCategory.TRANSFER));

            // Update balances
            Account hubCenter = getAccountById(accounts, hub1_center);
            Account leafAcc = getAccountById(accounts, leaf);
            if (hubCenter != null && leafAcc != null) {
                hubCenter.debitBalance(h1Amt);
                leafAcc.creditBalance(h1Amt);
            }
        }

        // 3. Rapid Hop timing-based chain on Jan 18 (4 nodes, 3 hops)
        LocalDateTime rhTime = LocalDateTime.of(2024, 1, 18, 10, 0, 0);
        allTransactions.add(new Transaction(uid("RHP", fraudRnd), rh_1, rh_2, 65000.0, rhTime, "NORMAL",
                MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("RHP", fraudRnd), rh_1, rh_2, 65000.0, rhTime.plusSeconds(8), "NORMAL",
                MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("RHP", fraudRnd), rh_1, rh_2, 65000.0, rhTime.plusSeconds(16), "NORMAL",
                MerchantCategory.TRANSFER));

        allTransactions.add(new Transaction(uid("RHP", fraudRnd), rh_2, rh_3, 63500.0, rhTime.plusSeconds(30), "NORMAL",
                MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("RHP", fraudRnd), rh_2, rh_3, 63500.0, rhTime.plusSeconds(38), "NORMAL",
                MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("RHP", fraudRnd), rh_2, rh_3, 63500.0, rhTime.plusSeconds(46), "NORMAL",
                MerchantCategory.TRANSFER));

        allTransactions.add(new Transaction(uid("RHP", fraudRnd), rh_3, rh_4, 62000.0, rhTime.plusSeconds(60), "NORMAL",
                MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("RHP", fraudRnd), rh_3, rh_4, 62000.0, rhTime.plusSeconds(68), "NORMAL",
                MerchantCategory.TRANSFER));
        allTransactions.add(new Transaction(uid("RHP", fraudRnd), rh_3, rh_4, 62000.0, rhTime.plusSeconds(76), "NORMAL",
                MerchantCategory.TRANSFER));
        updateRapidHopBalances(accounts, rh_1, rh_2, rh_3, rh_4);

        // 4. Threshold Structuring (evading detection by staying just below ₹49,500)
        int[] thMerchants = { 81, 82, 83, 84, 85, 86, 87, 88, 89, 90 };
        for (int i = 0; i < 12; i++) {
            double ratio = 0.975 + (i * 0.002); // Varing Amt less than threashold
            double amount = Math.round(49500.0 * ratio * 100.0) / 100.0;
            String merchant = "ACC_" + String.format("%03d", thMerchants[fraudRnd.nextInt(thMerchants.length)]);
            LocalDateTime ts = LocalDateTime.of(2024, 1, 2 + i * 2, 9, 30);
            allTransactions.add(new Transaction(uid("THR", fraudRnd), th_1, merchant, amount, ts, "NORMAL",
                    MerchantCategory.TRANSFER));

            // Update balance
            Account thAccount = getAccountById(accounts, th_1);
            if (thAccount != null) {
                thAccount.debitBalance(amount);
            }
        }

        // Step 7: Hand off to analysis service
        fraudAnalysisService.initialize(accounts, allTransactions);
        System.out.println("[FraudLens] Seeded " + accounts.size()
                + " accounts, " + allTransactions.size() + " transactions.");
    }

    private String pickRandomAccount(Random rnd, Set<String> usedSet) {
        while (true) {
            int num = 1 + rnd.nextInt(80);
            String id = "ACC_" + String.format("%03d", num);
            if (!usedSet.contains(id)) {
                boolean consecutive = false;
                for (String used : usedSet) {
                    int usedNum = Integer.parseInt(used.substring(4));
                    if (Math.abs(num - usedNum) <= 1) {
                        consecutive = true;
                        break;
                    }
                }
                if (!consecutive) {
                    usedSet.add(id);
                    return id;
                }
            }
        }
    }

    private Account getAccountById(List<Account> accounts, String accountId) {
        for (Account acc : accounts) {
            if (acc.getAccountId().equals(accountId)) {
                return acc;
            }
        }
        return null;
    }

    private void updateAccountBalances(List<Account> accounts, String acc1, String acc2, String acc3, double amount) {
        Account a1 = getAccountById(accounts, acc1);
        Account a2 = getAccountById(accounts, acc2);
        Account a3 = getAccountById(accounts, acc3);

        if (a1 != null && a2 != null && a3 != null) {
            // Cycle: a1 -> a2 -> a3 -> a1 (2 rounds)
            for (int round = 0; round < 2; round++) {
                a1.debitBalance(amount);
                a2.creditBalance(amount);

                a2.debitBalance(amount);
                a3.creditBalance(amount);

                a3.debitBalance(amount);
                a1.creditBalance(amount);
            }
        }
    }

    private void updateRapidHopBalances(List<Account> accounts, String rh1, String rh2, String rh3, String rh4) {
        Account a1 = getAccountById(accounts, rh1);
        Account a2 = getAccountById(accounts, rh2);
        Account a3 = getAccountById(accounts, rh3);
        Account a4 = getAccountById(accounts, rh4);

        if (a1 != null && a2 != null && a3 != null && a4 != null) {
            // 3 transactions: a1->a2
            a1.debitBalance(65000 * 3);
            a2.creditBalance(65000 * 3);

            // 3 transactions: a2->a3
            a2.debitBalance(63500 * 3);
            a3.creditBalance(63500 * 3);

            // 3 transactions: a3->a4
            a3.debitBalance(62000 * 3);
            a4.creditBalance(62000 * 3);
        }
    }

    private String uid(String prefix, Random rnd) {
        return prefix + "_" + String.format("%08X", Math.abs(rnd.nextInt()));
    }
}