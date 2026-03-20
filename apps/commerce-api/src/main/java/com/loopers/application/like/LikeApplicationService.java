package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeDomainService;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.cache.LikeCountCacheService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final LikeCountCacheService likeCountCacheService;

    /**
     * 좋아요 등록.
     * 상품 존재 여부 검증 후 도메인 서비스 호출.
     * 트랜잭션 커밋 후 Redis 카운터 증가.
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @return 생성된 좋아요 결과
     */
    @Transactional
    public LikeResult like(Long userId, Long productId) {
        validateProductExists(productId);
        Like like = likeDomainService.like(userId, productId);

        registerAfterCommit(() -> incrementLikeCount(productId));

        return LikeResult.from(like);
    }

    /**
     * 좋아요 취소.
     * 멱등하게 동작 - 존재하지 않아도 예외 없이 처리.
     * 트랜잭션 커밋 후 Redis 카운터 감소.
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        boolean existed = likeDomainService.unlike(userId, productId);

        if (existed) {
            registerAfterCommit(() -> decrementLikeCount(productId));
        }
    }

    private void validateProductExists(Long productId) {
        productRepository.findByIdActive(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    private void incrementLikeCount(Long productId) {
        try {
            Long count = likeCountCacheService.increment(productId);
            log.debug("좋아요 카운터 증가: productId={}, count={}", productId, count);
        } catch (Exception e) {
            log.warn("좋아요 카운터 증가 실패 (무시): productId={}", productId, e);
        }
    }

    private void decrementLikeCount(Long productId) {
        try {
            Long count = likeCountCacheService.decrement(productId);
            log.debug("좋아요 카운터 감소: productId={}, count={}", productId, count);
        } catch (Exception e) {
            log.warn("좋아요 카운터 감소 실패 (무시): productId={}", productId, e);
        }
    }

    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
            );
        } else {
            action.run();
        }
    }
}
