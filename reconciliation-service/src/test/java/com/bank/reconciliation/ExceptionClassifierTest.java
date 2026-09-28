package com.bank.reconciliation;

import com.bank.reconciliation.config.ReconProperties;
import com.bank.reconciliation.dto.ExpectedLeg;
import com.bank.reconciliation.dto.LegMatchCandidate;
import com.bank.reconciliation.entity.oracle.TransactionMaster;
import com.bank.reconciliation.entity.postgres.LedgerMutationAudit;
import com.bank.reconciliation.entity.postgres.ReconResultAudit;
import com.bank.reconciliation.service.ExceptionClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionClassifierTest {

    private ExceptionClassifier classifier;

    @BeforeEach
    void setUp() {
        ReconProperties props = new ReconProperties();
        props.getMatching().setAmountTolerance(BigDecimal.ZERO);
        props.getMatching().setLatePostingThresholdSeconds(300);
        classifier = new ExceptionClassifier(props);
    }

    @Test
    void withdrawal_expandsToOneDebitLeg() {
        TransactionMaster txn = withdrawal("TXN-1", "ACC-1", "100.0000", "COMMITTED");
        List<ExpectedLeg> legs = ExpectedLeg.from(txn);

        assertEquals(1, legs.size());
        assertEquals("DEBIT", legs.get(0).mutationType());
        assertEquals("ACC-1", legs.get(0).accountId());
    }

    @Test
    void transfer_expandsToDebitAndCreditLegs() {
        TransactionMaster txn = transfer("TXN-2", "ACC-1", "ACC-2", "50.0000", "COMMITTED");
        List<ExpectedLeg> legs = ExpectedLeg.from(txn);

        assertEquals(2, legs.size());
        assertEquals("DEBIT", legs.get(0).mutationType());
        assertEquals("ACC-1", legs.get(0).accountId());
        assertEquals("CREDIT", legs.get(1).mutationType());
        assertEquals("ACC-2", legs.get(1).accountId());
    }

    @Test
    void missingLedgerEntry_whenNoActualLegExists() {
        ExpectedLeg expected = ExpectedLeg.from(withdrawal("TXN-3", "ACC-1", "20.0000", "COMMITTED")).get(0);

        ReconResultAudit result = classifier.classify(new LegMatchCandidate(expected, List.of()));

        assertEquals(ReconResultAudit.ReconStatus.EXCEPTION, result.getReconStatus());
        assertEquals(ReconResultAudit.ExceptionType.MISSING_LEDGER_ENTRY, result.getExceptionType());
        assertEquals(ReconResultAudit.Severity.CRITICAL, result.getSeverity());
    }

    @Test
    void duplicateEntry_whenTwoActualLegsMatchOneExpectedLeg() {
        ExpectedLeg expected = ExpectedLeg.from(withdrawal("TXN-4", "ACC-1", "30.0000", "COMMITTED")).get(0);
        LedgerMutationAudit leg1 = leg("TXN-4", "ACC-1", "DEBIT", "30.0000", "COMMITTED");
        LedgerMutationAudit leg2 = leg("TXN-4", "ACC-1", "DEBIT", "30.0000", "COMMITTED");

        ReconResultAudit result = classifier.classify(new LegMatchCandidate(expected, List.of(leg1, leg2)));

        assertEquals(ReconResultAudit.ExceptionType.DUPLICATE_ENTRY, result.getExceptionType());
    }

    @Test
    void accountMismatch_whenLedgerAccountDiffersFromExpected() {
        ExpectedLeg expected = ExpectedLeg.from(withdrawal("TXN-5", "ACC-1", "40.0000", "COMMITTED")).get(0);
        LedgerMutationAudit leg = leg("TXN-5", "ACC-9", "DEBIT", "40.0000", "COMMITTED");

        ReconResultAudit result = classifier.classify(new LegMatchCandidate(expected, List.of(leg)));

        assertEquals(ReconResultAudit.ExceptionType.ACCOUNT_MISMATCH, result.getExceptionType());
    }

    @Test
    void amountMismatch_whenExactMatchRequiredAndAmountsDiffer() {
        ExpectedLeg expected = ExpectedLeg.from(withdrawal("TXN-6", "ACC-1", "40.0000", "COMMITTED")).get(0);
        LedgerMutationAudit leg = leg("TXN-6", "ACC-1", "DEBIT", "40.0100", "COMMITTED");

        ReconResultAudit result = classifier.classify(new LegMatchCandidate(expected, List.of(leg)));

        assertEquals(ReconResultAudit.ExceptionType.AMOUNT_MISMATCH, result.getExceptionType());
    }

    @Test
    void statusMismatch_whenAuditStateDiffersFromTxnStatus() {
        ExpectedLeg expected = ExpectedLeg.from(withdrawal("TXN-7", "ACC-1", "10.0000", "COMMITTED")).get(0);
        LedgerMutationAudit leg = leg("TXN-7", "ACC-1", "DEBIT", "10.0000", "PENDING");

        ReconResultAudit result = classifier.classify(new LegMatchCandidate(expected, List.of(leg)));

        assertEquals(ReconResultAudit.ExceptionType.STATUS_MISMATCH, result.getExceptionType());
    }

    @Test
    void matched_whenEverythingAligns() {
        TransactionMaster txn = withdrawal("TXN-8", "ACC-1", "15.0000", "COMMITTED");
        ExpectedLeg expected = ExpectedLeg.from(txn).get(0);
        LedgerMutationAudit leg = leg("TXN-8", "ACC-1", "DEBIT", "15.0000", "COMMITTED");
        leg.setCreatedAt(txn.getCompletedAt().plusSeconds(2));

        ReconResultAudit result = classifier.classify(new LegMatchCandidate(expected, List.of(leg)));

        assertEquals(ReconResultAudit.ReconStatus.MATCHED, result.getReconStatus());
        assertNull(result.getExceptionType());
    }

    @Test
    void orphanLedgerEntry_hasNoExpectedLegAtAll() {
        LedgerMutationAudit orphan = leg("TXN-GHOST", "ACC-1", "CREDIT", "5.0000", "COMMITTED");

        ReconResultAudit result = classifier.classifyOrphan(orphan);

        assertEquals(ReconResultAudit.ExceptionType.ORPHAN_LEDGER_ENTRY, result.getExceptionType());
        assertEquals(ReconResultAudit.Severity.CRITICAL, result.getSeverity());
    }

    private TransactionMaster withdrawal(String txnId, String accountId, String amount, String status) {
        TransactionMaster t = new TransactionMaster();
        t.setTxnId(txnId);
        t.setTxnType("WITHDRAWAL");
        t.setDebitAccountId(accountId);
        t.setMutationAmount(new BigDecimal(amount));
        t.setTxnStatus(status);
        t.setInitiatedAt(OffsetDateTime.now());
        t.setCompletedAt(OffsetDateTime.now());
        t.setCreatedAt(OffsetDateTime.now());
        return t;
    }

    private TransactionMaster transfer(String txnId, String debitAcc, String creditAcc, String amount, String status) {
        TransactionMaster t = new TransactionMaster();
        t.setTxnId(txnId);
        t.setTxnType("TRANSFER");
        t.setDebitAccountId(debitAcc);
        t.setCreditAccountId(creditAcc);
        t.setMutationAmount(new BigDecimal(amount));
        t.setTxnStatus(status);
        t.setInitiatedAt(OffsetDateTime.now());
        t.setCompletedAt(OffsetDateTime.now());
        t.setCreatedAt(OffsetDateTime.now());
        return t;
    }

    private LedgerMutationAudit leg(String txnId, String accountId, String mutationType, String amount, String auditState) {
        LedgerMutationAudit m = new LedgerMutationAudit();
        m.setMutationUuid(UUID.randomUUID());
        m.setTxnId(txnId);
        m.setAccountId(accountId);
        m.setMutationType(mutationType);
        m.setMutationAmount(new BigDecimal(amount));
        m.setTxnType("WITHDRAWAL");
        m.setAuditState(auditState);
        m.setCreatedAt(OffsetDateTime.now());
        return m;
    }
}
