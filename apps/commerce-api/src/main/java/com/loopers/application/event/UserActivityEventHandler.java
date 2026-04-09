package com.loopers.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.event.PaymentCanceledEvent;
import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.PaymentFailedEvent;
import com.loopers.domain.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityEventHandler {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleProductViewed(ProductViewedEvent event) {
        try {
            log.info("상품 조회: viewerId={}, productId={}, occurredAt={}",
                    event.viewerId(), event.productId(), event.occurredAt());
            String payloadJson = objectMapper.writeValueAsString(event);
            Map<String, Object> envelope = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", "product.viewed",
                    "payload", payloadJson
            );
            kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, String.valueOf(event.productId()), envelope);
        } catch (Exception e) {
            log.error("상품 조회 이벤트 발행 실패: productId={}", event.productId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        try {
            log.info("결제 완료: paymentId={}, orderId={}, userId={}, amount={}",
                    event.paymentId(), event.orderId(), event.userId(), event.amount());
        } catch (Exception e) {
            log.error("결제 완료 로깅 실패: paymentId={}", event.paymentId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handlePaymentFailed(PaymentFailedEvent event) {
        try {
            log.info("결제 실패: paymentId={}, orderId={}, userId={}, reason={}",
                    event.paymentId(), event.orderId(), event.userId(), event.reason());
        } catch (Exception e) {
            log.error("결제 실패 로깅 실패: paymentId={}", event.paymentId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handlePaymentCanceled(PaymentCanceledEvent event) {
        try {
            log.info("결제 취소: paymentId={}, orderId={}, userId={}",
                    event.paymentId(), event.orderId(), event.userId());
        } catch (Exception e) {
            log.error("결제 취소 로깅 실패: paymentId={}", event.paymentId(), e);
        }
    }
}
