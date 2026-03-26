package com.loopers.infrastructure.metrics;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class EventHandledRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public boolean markHandledIfAbsent(String consumerGroup, UUID eventId) {
        int inserted = entityManager.createNativeQuery(
                        """
                        INSERT INTO event_handled (consumer_group, event_id, handled_at)
                        VALUES (:consumerGroup, UUID_TO_BIN(:eventId), NOW(6))
                        ON DUPLICATE KEY UPDATE handled_at = handled_at
                        """
                )
                .setParameter("consumerGroup", consumerGroup)
                .setParameter("eventId", eventId.toString())
                .executeUpdate();
        return inserted > 0;
    }
}
