package com.fraudlens.data;

import com.fraudlens.model.MerchantCategory;
import com.fraudlens.model.Profession;

import java.util.Set;

/**
 * Defines realistic behaviour profiles for different user types.
 */
public class BehaviourProfiles {

        public record BehaviourProfile(
                Profession profession,
                int minMonthlyTxns,
                int maxMonthlyTxns,
                double minTxnAmount,
                double maxTxnAmount,
                Set<MerchantCategory> preferredCategories,
                double merchantTxnProbability,
                double p2pProbability,
                double weekendMultiplier,
                boolean hasSalary,
                double salaryAmount,
                boolean hasRecurringPayments
        ) {}

    private static BehaviourProfile createProfile(
            Profession profession,
            int minTxns,
            int maxTxns,
            double minAmount,
            double maxAmount,
            Set<MerchantCategory> categories,
            double merchantProbability,
            double p2pProbability,
            double weekendMultiplier,
            boolean hasSalary,
            double salaryAmount,
            boolean hasRecurringPayments
    ) {
        return new BehaviourProfile(
                profession,
                minTxns,
                maxTxns,
                minAmount,
                maxAmount,
                categories,
                merchantProbability,
                p2pProbability,
                weekendMultiplier,
                hasSalary,
                salaryAmount,
                hasRecurringPayments
        );
    }

    private static final BehaviourProfile STUDENT = createProfile(
            Profession.STUDENT, 7, 15, 100, 2500, 
             Set.of(
                    MerchantCategory.RESTAURANT,
                    MerchantCategory.TELECOM,
                    MerchantCategory.ENTERTAINMENT,
                    MerchantCategory.SHOPPING
            ), 0.30,  0.70, 1.5, false, 0.0, false );

    private static final BehaviourProfile SALARIED = createProfile(
            Profession.SALARIED_EMPLOYEE, 12, 18, 200, 5000,
            Set.of(
                    MerchantCategory.GROCERY,
                    MerchantCategory.FUEL,
                    MerchantCategory.UTILITY,
                    MerchantCategory.RESTAURANT,
                    MerchantCategory.SHOPPING,
                    MerchantCategory.ENTERTAINMENT
            ), 0.40, 0.60, 1.3, true, 60000.0, true );

    private static final BehaviourProfile BUSINESS = createProfile(
            Profession.BUSINESS_OWNER, 20, 35, 500, 15000,
            Set.of(
                    MerchantCategory.UTILITY,
                    MerchantCategory.SHOPPING,
                    MerchantCategory.TELECOM,
                    MerchantCategory.EDUCATION
            ), 0.70, 0.30, 0.8, false,0.0, true );

    private static final BehaviourProfile FREELANCER = createProfile(
            Profession.FREELANCER, 5, 15, 300, 8000,
            Set.of(
                    MerchantCategory.UTILITY,
                    MerchantCategory.SHOPPING,
                    MerchantCategory.ENTERTAINMENT,
                    MerchantCategory.RESTAURANT
            ), 0.35, 0.65, 1.2, false,0.0,true );

    private static final BehaviourProfile RETIRED = createProfile(
            Profession.RETIRED, 6, 15, 200, 3000,
            Set.of(
                    MerchantCategory.GROCERY,
                    MerchantCategory.FUEL,
                    MerchantCategory.HOSPITAL,
                    MerchantCategory.UTILITY
            ),0.70,0.30,0.9,false,0.0, true);

    private static final BehaviourProfile MERCHANT = createProfile(
            Profession.MERCHANT,30, 50, 1000, 20000,
            Set.of(
                    //MerchantCategory.UTILITY,
                   // MerchantCategory.TELECOM
            ),0.0,0.0,1,false,0.0,false );

    /**
     * Returns the behaviour profile for a given profession.
     */
    public static BehaviourProfile forProfession(Profession profession) {
        return switch (profession) {
            case STUDENT -> STUDENT;
            case SALARIED_EMPLOYEE -> SALARIED;
            case BUSINESS_OWNER -> BUSINESS;
            case FREELANCER -> FREELANCER;
            case RETIRED -> RETIRED;
            case MERCHANT -> MERCHANT;
        };
    }
}