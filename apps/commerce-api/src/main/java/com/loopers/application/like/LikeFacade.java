package com.loopers.application.like;

import com.loopers.application.product.ProductCacheService;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → LikeInfo 변환.
 * 좋아요 추가/취소 시 PDP 캐시 무효화(로드맵 §4.2).
 */
@Service
public class LikeFacade {

    private final LikeService likeService;
    private final ProductCacheService productCacheService;

    public LikeFacade(LikeService likeService, ProductCacheService productCacheService) {
        this.likeService = likeService;
        this.productCacheService = productCacheService;
    }

    @Transactional
    public LikeInfo addLike(Long userId, Long productId) {
        LikeModel like = likeService.addLike(userId, productId);
        productCacheService.evictDetail(productId);
        return LikeInfo.from(like);
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        likeService.removeLike(userId, productId);
        productCacheService.evictDetail(productId);
    }

    @Transactional(readOnly = true)
    public Page<LikeInfo> findLikesByUserId(Long userId, Pageable pageable) {
        return likeService.findLikesByUserId(userId, pageable).map(LikeInfo::from);
    }
}
