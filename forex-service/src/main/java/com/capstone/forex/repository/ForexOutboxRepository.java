package com.capstone.forex.repository;

import com.capstone.forex.entity.ForexOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ForexOutboxRepository extends JpaRepository<ForexOutbox, UUID> {

    List<ForexOutbox> findBySourceServiceAndStatusOrderByCreatedAtAsc(String sourceService, String status);

    List<ForexOutbox> findByStatusOrderByCreatedAtAsc(String status);
}
