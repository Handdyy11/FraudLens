package com.fraudlens.model;
import com.fraudlens.model.MerchantCategory;
import java.time.LocalDateTime;
 
public class Transaction {
    private String txnId;
    private String fromAccount;
    private String toAccount;
    private double amount;
    private LocalDateTime timestamp;
    private String type; // "NORMAL" or "FRAUD"
    private MerchantCategory category; // Transaction category

    // Legacy constructor for backward compatibility
    public Transaction(String txnId, String fromAccount, String toAccount,
                       double amount, LocalDateTime timestamp, String type) {
        this(txnId, fromAccount, toAccount, amount, timestamp, type, MerchantCategory.TRANSFER);
    }

    // Enhanced constructor with category
    public Transaction(String txnId, String fromAccount, String toAccount,
                       double amount, LocalDateTime timestamp, String type, MerchantCategory category) {
        this.txnId = txnId;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.timestamp = timestamp;
        this.type = type;
        this.category = category;
    }

    public String getTxnId()        { return txnId; }
    public String getFromAccount()  { return fromAccount; }
    public String getToAccount()    { return toAccount; }
    public double getAmount()       { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getType()         { return type; }
    public MerchantCategory getCategory() { return category; }
}
