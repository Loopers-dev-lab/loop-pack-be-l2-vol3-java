package com.loopers.application.productlike;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.ProductLikedEvent;
import com.loopers.domain.event.ProductUnlikedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.productlike.ProductLike;
import com.loopers.domain.productlike.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductLikeFacade {

    private final ProductLikeService productLikeService;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProductLikeInfo registerLike(Long userId, Long productId) {
        // 상품 존재 확인
        productService.getById(productId);

        // 좋아요 등록
        ProductLike productLike = productLikeService.registerLike(userId, productId);

        // Outbox 이벤트 저장 (같은 트랜잭션)
        saveOutboxEvent("PRODUCT_LIKED", productId, userId);

        // 좋아요 커밋 후 likesCount 업데이트 이벤트 발행
        eventPublisher.publishEvent(new ProductLikedEvent(userId, productId));

        return ProductLikeInfo.from(productLike);
    }

    @Transactional
    public void cancelLike(Long userId, Long productId) {
        // 상품 존재 확인
        productService.getById(productId);

        // 좋아요 취소
        productLikeService.cancelLike(userId, productId);

        // Outbox 이벤트 저장 (같은 트랜잭션)
        saveOutboxEvent("PRODUCT_UNLIKED", productId, userId);

        // 좋아요 취소 커밋 후 likesCount 업데이트 이벤트 발행
        eventPublisher.publishEvent(new ProductUnlikedEvent(userId, productId));
    }

    private void saveOutboxEvent(String eventType, Long productId, Long userId) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("userId", userId, "productId", productId));
            OutboxEvent outboxEvent = OutboxEvent.create("catalog-events", eventType, String.valueOf(productId), payload);
            outboxEventService.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Outbox 이벤트 직렬화 실패", e);
        }
    }

    public Page<ProductLikeInfo> getLikesByUserId(Long userId, Pageable pageable) {
        return productLikeService.getLikesByUserId(userId, pageable)
                .map(ProductLikeInfo::from);
    }
}
