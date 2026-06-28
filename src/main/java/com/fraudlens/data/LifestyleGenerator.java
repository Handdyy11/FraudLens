package com.fraudlens.data;

import com.fraudlens.model.Account;
import com.fraudlens.model.MerchantCategory;
import com.fraudlens.model.Transaction;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Enhanced LifestyleGenerator: Creates realistic daily transactions for accounts.
 * 
 * Features:
 * - Respects behaviour profiles (profession-specific patterns)
 * - Maintains account balance (transactions fail if insufficient funds)
 * - Uses preferred counterparties (family, friends, merchants)
 * - Generates realistic transaction amounts based on merchant category
 * - Respects time-based and weekend behaviour
 * - Categorizes transactions appropriately
 */
public class LifestyleGenerator {

    private static final LocalDateTime JAN_START = LocalDateTime.of(2024, 1, 1, 0, 0);
    // Preferred counterparties for each account (generated deterministically)
    private final Map<Integer, List<Integer>> preferredCounterparties = new HashMap<>();
    // Merchant categories assigned to merchant accounts (81-100)
    private final Map<String, MerchantCategory> merchantCategories = new HashMap<>();
    private final Random rnd;

    public LifestyleGenerator(long seed) {
        this.rnd = new Random(seed);
        initializeCounterparties();
        initializeMerchantCategories();
    }

    private void initializeCounterparties() {
        // Generate preferred counterparties for each account (deterministically)
        for (int i = 1; i <= 80; i++) {
            List<Integer> preferred = new ArrayList<>();
            Random accRnd = new Random(i * 997L); // Seeded per account

            // Generate 3-5 preferred contacts
            int numPreferred = 3 + accRnd.nextInt(3);
            for (int j = 0; j < numPreferred; j++) {
                int peer;
                do {
                    peer = 1 + accRnd.nextInt(80);
                } while (peer == i || preferred.contains(peer));
                preferred.add(peer);
            }

            preferredCounterparties.put(i, preferred);
        }
    }
    
    //fills the merchantCategories map with merchant accounts and their corresponding categories.
    private void initializeMerchantCategories() {
        // Assign merchant categories to accounts 81-100
        MerchantCategory[] categories = MerchantCategory.values();
        for (int i = 81; i <= 100; i++) {
            String merchantId = "ACC_" + String.format("%03d", i);
            int categoryIndex = (i - 81) % categories.length;
            merchantCategories.put(merchantId, categories[categoryIndex]);
        }
    }

    public List<Transaction> generateFor(Account account, List<Account> allAccounts) {
        List<Transaction> txns = new ArrayList<>();
        int accountNum = Integer.parseInt(account.getAccountId().substring(4));
        BehaviourProfiles.BehaviourProfile profile = BehaviourProfiles.forProfession(account.getProfession());

        // Skip merchant accounts cause they are revievers 
        if (accountNum > 80) {
            return txns;
        }

        // Determine number of transactions for this account
        int txnCount = (int) (profile.minMonthlyTxns() + rnd.nextDouble() * (profile.maxMonthlyTxns() - profile.minMonthlyTxns()));

        for (int i = 0; i < txnCount; i++) {
            // Decide: merchant transaction or peer-to-peer
            boolean isMerchantTxn = rnd.nextDouble() < profile.merchantTxnProbability();
            
            String toAccount;
            MerchantCategory category;
            double amount;

            if (isMerchantTxn) {
                toAccount = pickMerchantAccount(profile, rnd);
                category = merchantCategories.getOrDefault(toAccount, MerchantCategory.SHOPPING);
                amount = generateMerchantAmount(category, rnd);
            } else {
                toAccount = pickPeerAccount(accountNum, profile, rnd);
                category = MerchantCategory.TRANSFER;
                amount = generateP2PAmount(profile, rnd);
            }

            // Check if account has sufficient balance
            if (!account.hassufficientBalance(amount)) {
                continue; // Skip this transaction if not enough balance
            }

            LocalDateTime timestamp = generateRealisticTimestamp(profile, accountNum, rnd);

            Transaction txn = new Transaction(
                    uid(rnd),
                    account.getAccountId(),
                    toAccount,
                    Math.round(amount * 100.0) / 100.0,
                    timestamp,
                    "NORMAL",
                    category
            );

            txns.add(txn);
            account.debitBalance(amount);

            // Credit the receiving account (regular or merchant)
            for (Account acc : allAccounts) {
                if (acc.getAccountId().equals(toAccount)) {
                    acc.creditBalance(amount);
                    break;
                }
            }
        }

        return txns;
    }

    /**
     * Generates a realistic timestamp based on profession and time patterns.
     */
    private LocalDateTime generateRealisticTimestamp(BehaviourProfiles.BehaviourProfile profile, 
                                                     int accountNum, Random rnd) {
        // Random day in January
        int day = 1 + rnd.nextInt(28); // Stay within Jan 1-28
        LocalDateTime date = JAN_START.plusDays(day - 1);

        // Check if weekend and apply multiplier
        DayOfWeek dow = date.getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;

        // Weekend multiplier affects probability
        if (isWeekend && profile.weekendMultiplier() < 1.0) {
            // Lower chance of transaction on weekend if multiplier < 1
            if (rnd.nextDouble() > profile.weekendMultiplier()) {
                // Skip to next weekday
                if (dow == DayOfWeek.SATURDAY) {
                    date = date.plusDays(2);
                } else {
                    date = date.plusDays(1);
                }
            }
        }

        // Generate hour within active hours
        int hourFrom = profile.profession().getPreferredHourStart();
        int hourTo = profile.profession().getPreferredHourEnd();
        int hour = hourFrom + rnd.nextInt(Math.max(1, hourTo - hourFrom));
        int minute = rnd.nextInt(60);

        return date.withHour(hour).withMinute(minute);
    }

    /**
     * Picks a merchant account based on merchant categories the account prefers.
     */
    private String pickMerchantAccount(BehaviourProfiles.BehaviourProfile profile, Random rnd) {
        Set<MerchantCategory> preferred = profile.preferredCategories();
        List<String> candidateMerchants = new ArrayList<>();

        // Find merchants with preferred categories
        for (Map.Entry<String, MerchantCategory> entry : merchantCategories.entrySet()) {
            if (preferred.contains(entry.getValue())) {
                candidateMerchants.add(entry.getKey());
            }
        }

        // If none found, return any random merchant
        if (candidateMerchants.isEmpty()) {
            int merchantNum = 81 + rnd.nextInt(20);
            return "ACC_" + String.format("%03d", merchantNum);
        }

        return candidateMerchants.get(rnd.nextInt(candidateMerchants.size()));
    }

    /**
     * Picks a peer account from preferred counterparties.
     */
    private String pickPeerAccount(int senderNum, BehaviourProfiles.BehaviourProfile profile, Random rnd) {
        List<Integer> preferred = preferredCounterparties.getOrDefault(senderNum, new ArrayList<>());

        if (!preferred.isEmpty() && rnd.nextDouble() < 0.7) {
            // 70% chance: send to preferred counterparty
            int peerNum = preferred.get(rnd.nextInt(preferred.size()));
            return "ACC_" + String.format("%03d", peerNum);
        }

        // 30% chance: send to random account
        int peerNum = 1 + rnd.nextInt(80);
        while (peerNum == senderNum) {
            peerNum = 1 + rnd.nextInt(80);
        }
        return "ACC_" + String.format("%03d", peerNum);
    }

    /**
     * Generates realistic merchant transaction amount based on merchant category.
     */
    private double generateMerchantAmount(MerchantCategory category, Random rnd) {
        double min = category.getMinAmount();
        double max = category.getMaxAmount();
        return min + rnd.nextDouble() * (max - min);
    }

    /**
     * Generates peer-to-peer transaction amount.
     */
    private double generateP2PAmount(BehaviourProfiles.BehaviourProfile profile, Random rnd) {
        double min = profile.minTxnAmount();
        double max = profile.maxTxnAmount();
        return min + rnd.nextDouble() * (max - min);
    }


    
    private static String uid(Random rnd) {
        return "LST_" + String.format("%08X", Math.abs(rnd.nextInt()));
    }
}
