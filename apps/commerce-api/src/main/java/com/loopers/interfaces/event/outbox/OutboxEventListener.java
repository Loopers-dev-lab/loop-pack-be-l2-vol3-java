package com.loopers.interfaces.event.outbox;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.order.OrderEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventProducer;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.domain.outbox.OutboxEventWriter;
import com.loopers.domain.product.ProductEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 도메인 이벤트를 수신하여 Outbox 저장 및 Kafka 발행을 담당하는 리스너.
 *
 * <p>BEFORE_COMMIT 단계에서 Outbox 테이블에 저장하고,
 * AFTER_COMMIT 단계에서 선점 후 Kafka로 발행한다.
 * 선점 실패 시 Relay 스케줄러에 위임한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventListener {

    private final OutboxEventWriter outboxEventWriter;
    private final OutboxEventService outboxEventService;
    private final OutboxEventProducer outboxEventProducer;

    /**
     * 좋아요 이벤트를 Outbox 테이블에 저장한다.
     *
     * @param event 좋아요 생성 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void saveOutbox(LikeEvent.Liked event) {
        log.info("[OUTBOX:Liked] productId={}, eventId={}", event.productId(), event.eventId());
        outboxEventWriter.write(
                event.eventId(),
                event.productId(),
                "LIKE",
                "LIKED",
                event,
                "like-liked-v1",
                String.valueOf(event.productId())
        );
    }

    /**
     * 좋아요 취소 이벤트를 Outbox 테이블에 저장한다.
     *
     * @param event 좋아요 취소 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void saveOutbox(LikeEvent.Unliked event) {
        log.info("[OUTBOX:Unliked] productId={}, eventId={}", event.productId(), event.eventId());
        outboxEventWriter.write(
                event.eventId(),
                event.productId(),
                "LIKE",
                "UNLIKED",
                event,
                "like-unliked-v1",
                String.valueOf(event.productId())
        );
    }

    /**
     * 주문 완료 이벤트를 Outbox 테이블에 저장한다.
     *
     * <p>BEFORE_COMMIT 단계에서 동작하여, 주문 상태 변경과 Outbox 저장이
     * 동일 트랜잭션 내에서 원자적으로 처리된다.</p>
     *
     * @param event 주문 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void saveOutbox(OrderEvent.OrderCompleted event) {
        log.info("[OUTBOX:OrderCompleted] orderId={}, eventId={}", event.orderId(), event.eventId());
        outboxEventWriter.write(
                event.eventId(),
                event.orderId(),
                "ORDER",
                "ORDER_COMPLETED",
                event,
                "order-completed-v1",
                String.valueOf(event.orderId())
        );
    }

    /**
     * Outbox 이벤트를 선점하여 Kafka로 발행한다.
     *
     * <p>AFTER_COMMIT 단계에서 동작하며, 원자적 UPDATE로 선점을 시도한다.
     * 선점 실패 시(Relay 스케줄러가 이미 처리 중) 발행을 건너뛰고,
     * Kafka 발행 실패 시 PUBLISH_FAILED로 변경하여 Relay 스케줄러에 재시도를 위임한다.</p>
     *
     * @param event 주문 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishToKafka(OrderEvent.OrderCompleted event) {
        if (!outboxEventService.publish(event.eventId())) {
            log.info("[OUTBOX:OrderCompleted] 선점 실패 (이미 처리 중): eventId={}", event.eventId());
            return;
        }

        OutboxEvent outboxEvent = outboxEventService.findById(event.eventId());
        outboxEventProducer.produceEvent(
                outboxEvent,
                () -> log.info("[OUTBOX:OrderCompleted] 발행 성공: topic={}, aggregateId={}",
                        outboxEvent.getTopic(), outboxEvent.getAggregateId()),
                ex -> {
                    outboxEventService.publishFail(outboxEvent.getId());
                    log.warn("[OUTBOX:OrderCompleted] 발행 실패, Relay 스케줄러에 위임: topic={}, aggregateId={}",
                            outboxEvent.getTopic(), outboxEvent.getAggregateId(), ex);
                }
        );
    }

    /**
     * 상품 삭제 이벤트를 Outbox 테이블에 저장한다.
     *
     * @param event 상품 삭제 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void saveOutbox(ProductEvent.ProductDeleted event) {
        log.info("[OUTBOX:ProductDeleted] productId={}, eventId={}", event.productId(), event.eventId());
        outboxEventWriter.write(
                event.eventId(),
                event.productId(),
                "PRODUCT",
                "PRODUCT_DELETED",
                event,
                "product-deleted-v1",
                String.valueOf(event.productId())
        );
    }

    /**
     * 상품 조회 이벤트를 Outbox 테이블에 저장한다.
     *
     * <p>조회 API는 쓰기 트랜잭션이 없으므로 {@code @TransactionalEventListener}가 아닌
     * {@code @EventListener} + {@code @Transactional}로 별도 트랜잭션을 생성하여 Outbox에 저장한다.
     * Kafka 발행은 Relay 스케줄러(1초 주기)에 위임한다.</p>
     *
     * @param event 상품 조회 이벤트
     */
    @EventListener
    @Transactional
    public void saveOutbox(ProductEvent.ProductViewed event) {
        log.info("[OUTBOX:ProductViewed] productId={}, eventId={}", event.productId(), event.eventId());
        outboxEventWriter.write(
                event.eventId(),
                event.productId(),
                "PRODUCT",
                "PRODUCT_VIEWED",
                event,
                "product-viewed-v1",
                String.valueOf(event.productId())
        );
    }
}
