package com.bank.reconciliation.service;

import com.bank.reconciliation.config.ReconProperties;
import com.bank.reconciliation.dto.ExpectedLeg;
import com.bank.reconciliation.dto.LegMatchCandidate;
import com.bank.reconciliation.entity.postgres.LedgerMutationAudit;
import com.bank.reconciliation.entity.postgres.ReconResultAudit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Pure classification logic - no DB access, so it's unit-testable on
 * its own. Checked in this order, first match wins:
 *
 *   1. no actual leg at all                    -> MISSING_LEDGER_ENTRY (CRITICAL)
 *   2. more than one actual leg for the key     -> DUPLICATE_ENTRY      (HIGH)
 *   3. leg's account_id != expected account_id  -> ACCOUNT_MISMATCH     (HIGH)
 *   4. |mutation_amount delta| > tolerance       -> AMOUNT_MISMATCH      (HIGH)
 *   5. leg.audit_state != txn.txn_status         -> STATUS_MISMATCH      (MEDIUM)
 *      (both use the same PENDING/COMMITTED/ROLLED_BACK vocabulary by design)
 *   6. posting lag over threshold                -> LATE_POSTING         (LOW)
 *   7. otherwise                                  -> MATCHED
 *
 * A ledger leg whose (txn_id, mutation_type) matched no expected leg
 * at all is classified separately via {@link #classifyOrphan}.
 */
@Component
public class ExceptionClassifier {

    private final ReconProperties props;

    public ExceptionClassifier(ReconProperties props) {
        this.props = props;
    }

    public ReconResultAudit classify(LegMatchCandidate candidate) {
        ExpectedLeg expected = candidate.expected();
        var legs = candidate.actualLegs();

        ReconResultAudit result = new ReconResultAudit();
        result.setTxnId(expected.txnId());
        result.setAccountId(expected.accountId());
        result.setExpectedAmount(expected.expectedAmount());
        result.setTxnStatus(expected.txnStatus());
        result.setTxnCompletedAt(expected.completedAt());

        if (legs.isEmpty()) {
            return exception(result, ReconResultAudit.ExceptionType.MISSING_LEDGER_ENTRY,
                    ReconResultAudit.Severity.CRITICAL);
        }

        if (legs.size() > 1) {
            LedgerMutationAudit first = legs.get(0);
            LedgerMutationAudit second = legs.get(1);
            result.setMutationUuid(first.getMutationUuid());
            result.setDuplicateMutationUuid(second.getMutationUuid());
            result.setActualAmount(second.getMutationAmount());
            result.setLedgerAuditState(second.getAuditState());
            result.setLedgerPostedAt(second.getCreatedAt());
            return exception(result, ReconResultAudit.ExceptionType.DUPLICATE_ENTRY,
                    ReconResultAudit.Severity.HIGH);
        }

        LedgerMutationAudit leg = legs.get(0);
        result.setMutationUuid(leg.getMutationUuid());
        result.setActualAmount(leg.getMutationAmount());
        result.setLedgerAuditState(leg.getAuditState());
        // ledger_mutation_audit has no posted_at column - created_at IS the
        // posting time, since rows are append-only from the moment they exist.
        result.setLedgerPostedAt(leg.getCreatedAt());

        if (!leg.getAccountId().equals(expected.accountId())) {
            return exception(result, ReconResultAudit.ExceptionType.ACCOUNT_MISMATCH,
                    ReconResultAudit.Severity.HIGH);
        }

        BigDecimal delta = leg.getMutationAmount().subtract(expected.expectedAmount()).abs();
        if (delta.compareTo(props.getMatching().getAmountTolerance()) > 0) {
            return exception(result, ReconResultAudit.ExceptionType.AMOUNT_MISMATCH,
                    ReconResultAudit.Severity.HIGH);
        }

        if (!leg.getAuditState().equals(expected.txnStatus())) {
            return exception(result, ReconResultAudit.ExceptionType.STATUS_MISMATCH,
                    ReconResultAudit.Severity.MEDIUM);
        }

        if (expected.completedAt() != null) {
            long lagSeconds = Duration.between(expected.completedAt(), leg.getCreatedAt()).getSeconds();
            result.setPostingLagSeconds((int) lagSeconds);
            if (lagSeconds > props.getMatching().getLatePostingThresholdSeconds()) {
                return exception(result, ReconResultAudit.ExceptionType.LATE_POSTING,
                        ReconResultAudit.Severity.LOW);
            }
        }

        result.setReconStatus(ReconResultAudit.ReconStatus.MATCHED);
        result.setExceptionType(null);
        result.setSeverity(ReconResultAudit.Severity.LOW);
        return result;
    }

    /** A ledger leg whose (txn_id, mutation_type) matched no expected leg at all. */
    public ReconResultAudit classifyOrphan(LedgerMutationAudit orphanLeg) {
        ReconResultAudit result = new ReconResultAudit();
        result.setTxnId(orphanLeg.getTxnId());
        result.setAccountId(orphanLeg.getAccountId());
        result.setMutationUuid(orphanLeg.getMutationUuid());
        result.setActualAmount(orphanLeg.getMutationAmount());
        result.setLedgerAuditState(orphanLeg.getAuditState());
        result.setLedgerPostedAt(orphanLeg.getCreatedAt());
        return exception(result, ReconResultAudit.ExceptionType.ORPHAN_LEDGER_ENTRY,
                ReconResultAudit.Severity.CRITICAL);
    }

    private ReconResultAudit exception(ReconResultAudit result, ReconResultAudit.ExceptionType type,
                                        ReconResultAudit.Severity severity) {
        result.setReconStatus(ReconResultAudit.ReconStatus.EXCEPTION);
        result.setExceptionType(type);
        result.setSeverity(severity);
        return result;
    }
}
