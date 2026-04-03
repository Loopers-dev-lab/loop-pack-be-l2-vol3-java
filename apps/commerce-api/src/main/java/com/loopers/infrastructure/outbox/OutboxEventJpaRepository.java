package com.loopers.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    // 미발행 + 미실패 건만 조회 (실패 확정된 이벤트는 재시도하지 않음)
    List<OutboxEvent> findTop100ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();

    // 개별 이벤트 발행 시 SELECT ... FOR UPDATE — 스케줄러 중복 실행 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OutboxEvent o WHERE o.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") Long id);
}
