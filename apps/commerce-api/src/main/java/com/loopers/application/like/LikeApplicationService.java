package com.loopers.application.like;

import com.loopers.application.event.LikeCanceledEvent;
import com.loopers.application.event.LikeCreatedEvent;
import com.loopers.application.event.UserActionEvent;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeDomainService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.useraction.ActionType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 Application Service.
 * 여러 BC 조합 및 트랜잭션 경계 담당.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeApplicationService {

    private final LikeDomainService likeDomainService;
    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 좋아요 등록.
     * 상품 존재 여부 검증 후 도메인 서비스 호출.
     * 트랜잭션 커밋 후 Redis 카운터 증가 이벤트 발행.
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @return 생성된 좋아요 결과
     */
    @Transactional
    public LikeResult like(Long userId, Long productId) {
        validateProductExists(productId);
        Like like = likeDomainService.like(userId, productId);

        eventPublisher.publishEvent(LikeCreatedEvent.of(like.getId(), userId, productId));
        eventPublisher.publishEvent(UserActionEvent.of(userId, ActionType.LIKE, productId));

        return LikeResult.from(like);
    }

    /**
     * 좋아요 취소.
     * 멱등하게 동작 - 존재하지 않아도 예외 없이 처리.
     * 트랜잭션 커밋 후 Redis 카운터 감소 이벤트 발행.
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
            .ifPresent(like -> {
                Long likeId = like.getId();
                likeRepository.delete(like);
                eventPublisher.publishEvent(LikeCanceledEvent.of(likeId, userId, productId));
            });
    }

    private void validateProductExists(Long productId) {
        productRepository.findByIdActive(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }
}
