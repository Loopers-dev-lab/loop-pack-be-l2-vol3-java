package com.loopers.application.like;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikedEvent;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.kafka.event.CatalogEvent;
import com.loopers.kafka.topic.KafkaTopics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    // 이 메서드의 책임은 "좋아요 저장"까지다.
    // product.likesCount 업데이트는 LikedEventListener가 AFTER_COMMIT에서 처리한다.
    // Outbox에 이벤트를 같은 TX로 저장 → OutboxPublisher가 Kafka로 발행 (At Least Once 보장)
    @SneakyThrows
    @Transactional
    public void like(Long memberId, Long productId) {
        if (likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        if (!productRepository.existsById(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
        likeRepository.save(new Like(memberId, productId));

        // Outbox에 저장 (Like 저장과 같은 TX → 원자적 보장)
        String eventId = UUID.randomUUID().toString();
        CatalogEvent event = new CatalogEvent(eventId, CatalogEvent.Type.LIKED.name(), productId, memberId, Instant.now().toEpochMilli());
        outboxEventRepository.save(OutboxEvent.create(eventId, KafkaTopics.CATALOG_EVENTS, String.valueOf(productId), objectMapper.writeValueAsString(event)));

        // JVM 내부 이벤트 (AFTER_COMMIT에서 likesCount 즉시 반영)
        eventPublisher.publishEvent(new LikedEvent(memberId, productId));
    }

    // TODO: UnlikedEvent 설계 후 동일한 패턴으로 이벤트 발행으로 교체 예정
    @Transactional
    public void unlike(Long memberId, Long productId) {
        if (!likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }
        if (!productRepository.existsById(productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
        likeRepository.deleteByMemberIdAndProductId(memberId, productId);
    }
}
