package com.loopers.application.like;

import com.loopers.application.product.ProductCacheService;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 좋아요 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → LikeInfo 변환.
 * 좋아요 추가/취소 시 PDP·PLP 1페이지 캐시 무효화(로드맵 §3.1.4, §4.2).
 */
@Service
public class LikeFacade {

    private final LikeService likeService;
    private final ProductCacheService productCacheService;

    public LikeFacade(LikeService likeService, ProductCacheService productCacheService) {
        this.likeService = likeService;
        this.productCacheService = productCacheService;
    }

    private static void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    @Transactional
    public LikeInfo addLike(Long userId, Long productId) {
        LikeModel like = likeService.addLike(userId, productId);
        // 좋아요 수 변경 시 커밋 이후 PDP + PLP 1페이지 캐시를 무효화해 정렬/카운트 일시 불일치 최소화
        runAfterCommit(() -> {
            productCacheService.evictDetail(productId);
            productCacheService.evictList();
        });
        return LikeInfo.from(like);
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        likeService.removeLike(userId, productId);
        runAfterCommit(() -> {
            productCacheService.evictDetail(productId);
            productCacheService.evictList();
        });
    }

    @Transactional(readOnly = true)
    public Page<LikeInfo> findLikesByUserId(Long userId, Pageable pageable) {
        return likeService.findLikesByUserId(userId, pageable).map(LikeInfo::from);
    }
}
