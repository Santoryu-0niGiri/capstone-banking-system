
package com.capstone.common.constants;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    // ── Core transaction lifecycle (transaction-service → notification-service) ──
    /** Single fan-out topic for CREATED / COMPLETED / FAILED events. */
    public static final String TRANSACTION_EVENTS      = "transaction-events";

    // ── Registration & Account provisioning ─────────────────────────────────────
    public static final String CUSTOMER_REGISTERED     = "customer.registered";
    public static final String ACCOUNT_CREATED         = "account.created";

    // ── Balance mutations ────────────────────────────────────────────────────────
    public static final String BALANCE_UPDATED         = "balance.updated";

    // ── ForEx settlement (cross-currency transfers) ──────────────────────────────
    /**
     * Published by transaction-service relay (OUTBOX_MAIN) when a cross-currency
     * TRANSFER is staged. Consumed by ForEx Service to fetch rate and calculate
     * the destination amount.
     */
    public static final String FOREX_CONVERSION_REQUESTED  = "forex.conversion.requested";

    /**
     * Published by ForEx Service after rate lookup and amount calculation.
     * Consumed by accounts-service to apply the converted credit leg.
     */
    public static final String FOREX_CONVERSION_COMPLETED  = "forex.conversion.completed";

    /**
     * Published by accounts-service after the cross-currency credit leg is applied.
     * Consumed by transaction-service to mark the transfer COMMITTED.
     */
    public static final String CROSSCURRENCY_SETTLEMENT_COMPLETED = "crosscurrency.settlement.completed";

    // ── Reconciliation ───────────────────────────────────────────────────────────
    public static final String RECON_DISCREPANCY       = "reconciliation.discrepancy";

    // ── Ledger audit events (OUTBOX_AUDIT relay, Postgres side) ─────────────────
    /** Published by the Postgres outbox relay after a ledger_mutation_audit row is posted. */
    public static final String LEDGER_MUTATION_POSTED  = "ledger.mutation.posted";
}

