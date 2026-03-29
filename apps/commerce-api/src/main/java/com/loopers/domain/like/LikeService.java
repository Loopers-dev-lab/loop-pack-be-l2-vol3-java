package com.loopers.domain.like;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxEventTopics;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 좋아요 등록.
     * - 상품 존재 여부 검증
     * - 중복 좋아요는 409 반환 (race condition 대비 DataIntegrityViolationException 처리)
     * - 성공 시 LikeCreatedEvent 발행 (비동기 카운트 업데이트)
     */
    @Transactional
    public Like addLike(Long userId, Long productId) {
        productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }

        try {
            Like like = likeRepository.save(new Like(userId, productId));
            eventPublisher.publishEvent(new LikeCreatedEvent(userId, productId));
            outboxEventRepository.save(OutboxEvent.create(
                "LIKE_CREATED",
                OutboxEventTopics.PRODUCT_LIKE,
                serializePayload(Map.of("userId", userId, "productId", productId)),
                productId.toString()
            ));
            return like;
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 unique constraint 위반 시 409 반환
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
    }

    /**
     * 좋아요 취소 (멱등성 보장).
     * - 좋아요가 없으면 에러 로그만 남기고 성공 응답
     * - 성공 시 LikeDeletedEvent 발행 (비동기 카운트 업데이트)
     */
    @Transactional
    public void removeLike(Long userId, Long productId) {
        if (!likeRepository.existsByUserIdAndProductId(userId, productId)) {
            log.warn("좋아요 취소 요청: 좋아요가 존재하지 않습니다. [userId={}, productId={}]", userId, productId);
            return;
        }
        likeRepository.deleteByUserIdAndProductId(userId, productId);
        eventPublisher.publishEvent(new LikeDeletedEvent(userId, productId));
        outboxEventRepository.save(OutboxEvent.create(
            "LIKE_DELETED",
            OutboxEventTopics.PRODUCT_LIKE,
            serializePayload(Map.of("userId", userId, "productId", productId)),
            productId.toString()
        ));
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 직렬화 실패", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<Like> getMyLikes(Long userId, Pageable pageable) {
        return likeRepository.findAllByUserId(userId, pageable);
    }
}
