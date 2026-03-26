package com.loopers.infrastructure.order;

import com.loopers.application.order.OrderCancelRequestedEventMessage;
import com.loopers.application.order.OrderCreatedEventMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
public class OrderEventLogRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void saveOrderCreated(OrderCreatedEventMessage message) {
        entityManager.createNativeQuery(
                        """
                        INSERT INTO order_event_log (
                            event_id,
                            event_type,
                            order_id,
                            member_id,
                            total_amount,
                            occurred_at,
                            created_at
                        ) VALUES (
                            UUID_TO_BIN(:eventId),
                            'ORDER_CREATED',
                            :orderId,
                            :memberId,
                            :totalAmount,
                            :occurredAt,
                            NOW(6)
                        )
                        """
                )
                .setParameter("eventId", message.eventId().toString())
                .setParameter("orderId", message.orderId().toString())
                .setParameter("memberId", message.memberId())
                .setParameter("totalAmount", message.totalAmount())
                .setParameter("occurredAt", ZonedDateTime.ofInstant(message.occurredAt(), ZonedDateTime.now().getZone()))
                .executeUpdate();
    }

    public void saveOrderCancelRequested(OrderCancelRequestedEventMessage message) {
        entityManager.createNativeQuery(
                        """
                        INSERT INTO order_event_log (
                            event_id,
                            event_type,
                            order_id,
                            member_id,
                            total_amount,
                            occurred_at,
                            created_at
                        ) VALUES (
                            UUID_TO_BIN(:eventId),
                            'ORDER_CANCEL_REQUESTED',
                            :orderId,
                            :memberId,
                            NULL,
                            :occurredAt,
                            NOW(6)
                        )
                        """
                )
                .setParameter("eventId", message.eventId().toString())
                .setParameter("orderId", message.orderId().toString())
                .setParameter("memberId", message.memberId())
                .setParameter("occurredAt", ZonedDateTime.ofInstant(message.occurredAt(), ZonedDateTime.now().getZone()))
                .executeUpdate();
    }
}
