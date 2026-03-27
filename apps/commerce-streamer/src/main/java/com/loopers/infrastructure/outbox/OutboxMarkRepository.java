package com.loopers.infrastructure.outbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 셀프컨슘 역할: Consumer가 이벤트 처리 완료 후 Outbox 상태를 SENT로 갱신.
 * commerce-streamer가 같은 DB를 공유하므로 outbox_events 테이블에 직접 접근 가능.
 * Entity 매핑 없이 네이티브 쿼리로 처리 — streamer에 OutboxEvent Entity를 중복 정의하지 않음.
 */
@Repository
@RequiredArgsConstructor
public class OutboxMarkRepository {

    private final EntityManager entityManager;

    @Transactional
    public int markPublished(String eventId) {
        Query query = entityManager.createNativeQuery(
                "UPDATE outbox_events SET status = 'SENT', sent_at = NOW(6) WHERE event_id = :eventId AND status = 'PENDING'");
        query.setParameter("eventId", eventId);
        return query.executeUpdate();
    }
}
