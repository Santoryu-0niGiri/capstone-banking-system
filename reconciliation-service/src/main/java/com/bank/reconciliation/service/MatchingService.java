package com.bank.reconciliation.service;

import com.bank.reconciliation.dto.ExpectedLeg;
import com.bank.reconciliation.dto.LegMatchCandidate;
import com.bank.reconciliation.entity.oracle.TransactionMaster;
import com.bank.reconciliation.entity.postgres.LedgerMutationAudit;
import com.bank.reconciliation.repository.oracle.TransactionMasterRepository;
import com.bank.reconciliation.repository.postgres.LedgerMutationAuditRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds one {@link ExpectedLeg} per (WITHDRAWAL/DEPOSIT) transaction
 * or two (TRANSFER: a DEBIT leg and a CREDIT leg), then pairs each
 * against the actual ledger_mutation_audit rows sharing its
 * (txn_id, mutation_type) key. Anything actual left over - a ledger
 * row whose txn_id/mutation_type combination no expected leg claimed -
 * is a true orphan: either the transaction doesn't exist in this
 * window at all, or the mutation_type doesn't fit that txn_type
 * (e.g. a stray CREDIT row against a WITHDRAWAL-only transaction).
 */
@Service
public class MatchingService {

    private final TransactionMasterRepository transactionMasterRepository;
    private final LedgerMutationAuditRepository ledgerMutationAuditRepository;

    public MatchingService(TransactionMasterRepository transactionMasterRepository,
                            LedgerMutationAuditRepository ledgerMutationAuditRepository) {
        this.transactionMasterRepository = transactionMasterRepository;
        this.ledgerMutationAuditRepository = ledgerMutationAuditRepository;
    }

    public record WindowData(
            List<LegMatchCandidate> candidates,
            List<LedgerMutationAudit> orphanLedgerLegs
    ) {
    }

    public WindowData buildCandidates(OffsetDateTime windowStart, OffsetDateTime windowEnd) {
        List<TransactionMaster> transactions = transactionMasterRepository.findAllInWindow(windowStart, windowEnd);
        List<LedgerMutationAudit> actualLegs = ledgerMutationAuditRepository.findAllInWindow(windowStart, windowEnd);

        List<ExpectedLeg> expectedLegs = transactions.stream()
                .flatMap(txn -> ExpectedLeg.from(txn).stream())
                .toList();

        Map<String, List<LedgerMutationAudit>> actualByKey = actualLegs.stream()
                .collect(Collectors.groupingBy(leg -> ExpectedLeg.key(leg.getTxnId(), leg.getMutationType())));

        Set<String> expectedKeys = expectedLegs.stream().map(ExpectedLeg::key).collect(Collectors.toSet());

        List<LegMatchCandidate> candidates = new ArrayList<>(expectedLegs.size());
        for (ExpectedLeg expected : expectedLegs) {
            candidates.add(new LegMatchCandidate(expected, actualByKey.getOrDefault(expected.key(), List.of())));
        }

        List<LedgerMutationAudit> orphans = actualLegs.stream()
                .filter(leg -> !expectedKeys.contains(ExpectedLeg.key(leg.getTxnId(), leg.getMutationType())))
                .toList();

        return new WindowData(candidates, orphans);
    }
}
