package com.fraudlens.data;

import com.fraudlens.model.Account;
import com.fraudlens.model.Profession;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Factory Pattern: creates the 100 seeded accounts with profiles and balances.
 *
 * Account ranges:
 *   001–016  Students (cycle-pattern potential)
 *   017–040  Salaried & Freelancers (regular users)
 *   041–052  Business Owners (hub/threshold pattern potential)
 *   053–080  Mixed (salaried & retired, clean users)
 *   081–100  Merchant / utility accounts
 */
public class AccountFactory {

    private static final String[] NAMES = {
        // 001–010
        "Aarav Sharma",    "Priya Patel",     "Rohan Mehta",     "Sneha Gupta",     "Vikram Singh",
        "Ananya Reddy",    "Karan Joshi",     "Meera Iyer",      "Arjun Nair",      "Divya Kapoor",
        // 011–020
        "Rahul Verma",     "Pooja Desai",     "Aditya Rao",      "Neha Kulkarni",   "Siddharth Das",
        "Kavya Menon",     "Manish Tiwari",   "Ritu Agarwal",    "Amit Saxena",     "Shreya Pillai",
        // 021–030
        "Suresh Kumar",    "Lakshmi Bhat",    "Nikhil Pandey",   "Swati Mishra",    "Rajesh Thakur",
        "Deepa Naidu",     "Varun Choudhary", "Preeti Jain",     "Harish Hegde",    "Komal Shah",
        // 031–040
        "Ganesh Patil",    "Sunita Rathore",  "Ashok Shetty",    "Nandini Rao",     "Manoj Dubey",
        "Usha Prasad",     "Sanjay Bhatt",    "Rekha Mahajan",   "Vivek Chauhan",   "Anita Bose",
        // 041–050
        "Prakash Goel",    "Sarita Mathur",   "Dinesh Malhotra", "Jyoti Srivastava","Ramesh Khatri",
        "Padma Nambiar",   "Girish Tandon",   "Kavita Khanna",   "Sunil Wagh",      "Bhavna Sethi",
        // 051–060
        "Ajay Deshpande",  "Pallavi Mohan",   "Tushar Banerjee", "Rashmi Kaul",     "Vinod Sinha",
        "Archana Dixit",   "Pankaj Bajaj",    "Smita Gokhale",   "Gaurav Lal",      "Madhuri Vyas",
        // 061–070
        "Santosh Rawat",   "Rina Chopra",     "Nilesh Datta",    "Shilpa Purohit",  "Yash Grover",
        "Manju Rawal",     "Deepak Bhandari", "Sapna Dhawan",    "Tarun Khurana",   "Leela Narayan",
        // 071–080
        "Kishore Pandya",  "Suman Ahuja",     "Rajiv Mehra",     "Geeta Garg",      "Pravin Karnik",
        "Kamala Devi",     "Hemant Soni",     "Nisha Oberoi",    "Mukesh Arora",    "Asha Trivedi",
        // 081–090  (Merchants / Utilities)
        "PhonePe Merchant","Amazon Pay Seller","Flipkart Store",  "BigBasket Order", "Swiggy Delivery",
        "Zomato Payment",  "Uber Rides",      "Ola Cabs",        "BSNL Recharge",   "Airtel Payments",
        // 091–100  (Merchants / Utilities)
        "Jio Recharge",    "BESCOM Electric", "BWSSB Water",     "LIC Premium",     "MutualFund SIP",
        "Netflix India",   "Hotstar Sub",     "Gym Membership",  "Apartment Maint", "School Fees"
    };

    public List<Account> createAccounts() {
        List<Account> accounts = new ArrayList<>(100);
        Random rnd = new Random(42L); // Fixed seed for reproducibility

        for (int i = 1; i <= 100; i++) {
            String id = "ACC_" + String.format("%03d", i);
            String name = (i <= NAMES.length) ? NAMES[i - 1] : "User " + id;
            
            // Determine profession and financial profile based on account number
            Profession profession = determineProfession(i);
            double monthlyIncome = generateMonthlyIncome(profession, rnd);
            double initialBalance = monthlyIncome * 2 + rnd.nextDouble() * monthlyIncome;
            int age = generateAge(profession, rnd);

            accounts.add(new Account(id, name, profession, age, initialBalance, monthlyIncome));
        }
        return accounts;
    }

    /**
     * Assigns profession based on account number to maintain patterns.
     */
    private Profession determineProfession(int accountNum) {
        if (accountNum <= 16) {
            return Profession.STUDENT;
        } else if (accountNum <= 28) {
            return Profession.SALARIED_EMPLOYEE;
        } else if (accountNum <= 40) {
            return Profession.FREELANCER;
        } else if (accountNum <= 52) {
            return Profession.BUSINESS_OWNER;
        } else if (accountNum <= 70) {
            return Profession.SALARIED_EMPLOYEE;
        } else if (accountNum <= 80) {
            return Profession.RETIRED;
        } else {
            return Profession.MERCHANT;
        }
    }

    /**
     * Generates realistic monthly income based on profession.
     */
    private double generateMonthlyIncome(Profession profession, Random rnd) {
        double min = profession.getMinMonthlyIncome();
        double max = profession.getMaxMonthlyIncome();
        return min + rnd.nextDouble() * (max - min);
    }

    /**
     * Generates a realistic age based on profession.
     */
    private int generateAge(Profession profession, Random rnd) {
        return switch (profession) {
            case STUDENT -> 18 + rnd.nextInt(8);
            case SALARIED_EMPLOYEE -> 22 + rnd.nextInt(39);
            case BUSINESS_OWNER -> 28 + rnd.nextInt(38);
            case FREELANCER -> 22 + rnd.nextInt(34);
            case RETIRED -> 60 + rnd.nextInt(21);
            case MERCHANT -> 25 + rnd.nextInt(41);
        };
    }
}
