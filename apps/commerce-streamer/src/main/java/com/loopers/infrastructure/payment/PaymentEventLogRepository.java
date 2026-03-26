package com.loopers.infrastructure.payment;

import com.loopers.contract.kafka.PaymentStatusChangedOutboxMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
public class PaymentEventLogRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(PaymentStatusChangedOutboxMessage message) {
        entityManager.createNativeQuery(
                        """
                        INSERT INTO payment_event_log (
                            event_id,
                            event_type,
                            order_id,
                            member_id,
                            before_status,
                            after_status,
                            occurred_at,
                            created_at
                        ) VALUES (
                            UUID_TO_BIN(:eventId),
                            'PAYMENT_STATUS_CHANGED',
                            :orderId,
                            :memberId,
                            :beforeStatus,
                            :afterStatus,
                            :occurredAt,
                            NOW(6)
                        )
                        """
                )
                .setParameter("eventId", message.eventId().toString())
                .setParameter("orderId", message.orderId().toString())
                .setParameter("memberId", message.memberId())
                .setParameter("beforeStatus", message.beforeStatus())
                .setParameter("afterStatus", message.afterStatus())
                .setParameter("occurredAt", ZonedDateTime.ofInstant(message.occurredAt(), ZonedDateTime.now().getZone()))
                .executeUpdate();
    }
}
