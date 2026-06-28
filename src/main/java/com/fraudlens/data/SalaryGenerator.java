package com.fraudlens.data;

import com.fraudlens.model.Account;
import com.fraudlens.model.MerchantCategory;
import com.fraudlens.model.Transaction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates monthly salary transactions for salaried employees.
 * Salaries arrive from an employer account on dates (1st-7th of month).
 */
public class SalaryGenerator {

    private static final String EMPLOYER_ACCOUNT = "ACC_999"; // Virtual employer

    /**
     * Generates salary transactions for accounts with salary profile.
     */
    public List<Transaction> generateSalaries(List<Account> accounts, Random rnd) {
        List<Transaction> salaryTxns = new ArrayList<>();

        for (Account account : accounts) {
            BehaviourProfiles.BehaviourProfile profile = BehaviourProfiles.forProfession(account.getProfession());

            if (profile.hasSalary()) {
                // Generate monthly salary on random day between 1st-7th
                int salaryDay = 1 + rnd.nextInt(7);
                int hour = 8 + rnd.nextInt(3); // 8-11 AM
                int minute = rnd.nextInt(60);

                LocalDateTime salaryDate = LocalDateTime.of(2024, 1, salaryDay, hour, minute);
                double salaryAmount = profile.salaryAmount() + (rnd.nextDouble() * 2000 - 1000); // ±1000 variation

                Transaction salary = new Transaction(
                        uid(rnd),
                        EMPLOYER_ACCOUNT,
                        account.getAccountId(),
                        salaryAmount,
                        salaryDate,
                        "NORMAL",
                        MerchantCategory.SALARY);

                salaryTxns.add(salary);

                // Credit the account balance
                account.creditBalance(salaryAmount);
            }
        }

        return salaryTxns;
    }

    private static String uid(Random rnd) {
        return "SAL_" + String.format("%08X", Math.abs(rnd.nextInt()));
    }
}
