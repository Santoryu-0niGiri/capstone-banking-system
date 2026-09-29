package com.bank.reconciliation.dto;

import com.bank.reconciliation.entity.oracle.TransactionMaster;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One expected ledger_mutation_audit row, derived from a
 * transaction_master row's txn_type:
 *
 *   WITHDRAWAL -> one DEBIT  leg on debit_account_id
 *   DEPOSIT    -> one CREDIT leg on credit_account_id
 *   TRANSFER   -> one DEBIT  leg on debit_account_id
 *                 AND one CREDIT leg on credit_account_id
 *
 * recon_result_audit is one row per reconciled leg (per its own table
 * comment), not one row per transaction - so a TRANSFER yields two
 * recon_result_audit rows, one per expected leg.
 */
public record ExpectedLeg(
        String txnId,
        String accountId,
        String mutationType, // DEBIT / CREDIT
        BigDecimal expectedAmount,
        String txnStatus,
        String txnType,
        OffsetDateTime completedAt,
        String currencyCode
) {
    public static List<ExpectedLeg> from(TransactionMaster txn) {
        List<ExpectedLeg> legs = new ArrayList<>(2);
        switch (txn.getTxnType()) {
            case "WITHDRAWAL" -> legs.add(leg(txn, txn.getDebitAccountId(), "DEBIT"));
            case "DEPOSIT" -> legs.add(leg(txn, txn.getCreditAccountId(), "CREDIT"));
            case "TRANSFER" -> {
                legs.add(leg(txn, txn.getDebitAccountId(), "DEBIT"));
                legs.add(leg(txn, txn.getCreditAccountId(), "CREDIT"));
            }
            default -> throw new IllegalStateException("Unknown txn_type: " + txn.getTxnType());
        }
        return legs;
    }

    private static ExpectedLeg leg(TransactionMaster txn, String accountId, String mutationType) {
        BigDecimal amount = ("CREDIT".equals(mutationType) && txn.getDestAmount() != null)
                ? txn.getDestAmount()
                : txn.getMutationAmount();
        String currencyCode = "CREDIT".equals(mutationType) && txn.getDestCurrencyCode() != null
            ? txn.getDestCurrencyCode()
            : txn.getCurrencyCode();
        return new ExpectedLeg(txn.getTxnId(), accountId, mutationType, amount,
            txn.getTxnStatus(), txn.getTxnType(), txn.getCompletedAt(), currencyCode);
    }

    /** Groups actual ledger rows by this key to find each expected leg's match. */
    public String key() {
        return key(txnId, mutationType);
    }

    public static String key(String txnId, String mutationType) {
        return txnId + "|" + mutationType;
    }
}
