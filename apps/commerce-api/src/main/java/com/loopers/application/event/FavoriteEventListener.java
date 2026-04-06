package com.loopers.application.event;

import com.loopers.domain.event.FavoriteAddedEvent;
import com.loopers.domain.event.FavoriteRemovedEvent;
import com.loopers.domain.outbox.model.OutboxEvent;
import com.loopers.domain.outbox.model.OutboxEventType;
import com.loopers.domain.outbox.repository.OutboxEventRepository;
import com.loopers.domain.product.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class FavoriteEventListener {

    private final ProductService productService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFavoriteAdded(FavoriteAddedEvent event) {
        log.info("좋아요 집계 처리 - productId: {}, memberId: {}", event.productId(), event.memberId());
        productService.increaseLikeCount(event.productId());
        outboxEventRepository.save(OutboxEvent.create(
                OutboxEventType.FAVORITE_ADDED,
                String.valueOf(event.productId()),
                toJson(event)
        ));
        log.info("유저 행동 로깅 - memberId: {}, action: FAVORITE_ADD, targetId: {}", event.memberId(), event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFavoriteRemoved(FavoriteRemovedEvent event) {
        log.info("좋아요 집계 해제 처리 - productId: {}, memberId: {}", event.productId(), event.memberId());
        productService.decreaseLikeCount(event.productId());
        outboxEventRepository.save(OutboxEvent.create(
                OutboxEventType.FAVORITE_REMOVED,
                String.valueOf(event.productId()),
                toJson(event)
        ));
        log.info("유저 행동 로깅 - memberId: {}, action: FAVORITE_REMOVE, targetId: {}", event.memberId(), event.productId());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 직렬화 실패", e);
        }
    }
}
