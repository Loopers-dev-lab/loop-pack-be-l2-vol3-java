package com.loopers.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxJpaRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = "SELECT id FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit",
           nativeQuery = true)
    List<Long> findPendingIds(@Param("limit") int limit);

    @Query(value = "SELECT * FROM outbox_events WHERE id = :id AND status = 'PENDING' FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);
}
