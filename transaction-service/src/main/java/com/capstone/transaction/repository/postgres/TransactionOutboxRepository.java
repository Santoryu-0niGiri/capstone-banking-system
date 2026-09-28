package com.capstone.transaction.repository.postgres;

import com.capstone.transaction.entity.postgres.TransactionOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionOutboxRepository extends JpaRepository<TransactionOutbox, UUID> {
    List<TransactionOutbox> findByStatusOrderByCreatedAtAsc(String status);
}