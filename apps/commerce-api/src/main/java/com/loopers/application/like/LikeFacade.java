package com.loopers.application.like;

import com.loopers.application.product.event.ProductLikeChangedEvent;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → LikeInfo 변환.
 * 좋아요 추가/취소 시 PDP·PLP 1페이지 캐시 무효화(로드맵 §3.1.4, §4.2).
 */
@Service
public class LikeFacade {

    private final LikeService likeService;
    private final ApplicationEventPublisher eventPublisher;

    public LikeFacade(LikeService likeService, ApplicationEventPublisher eventPublisher) {
        this.likeService = likeService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public LikeInfo addLike(Long userId, Long productId) {
        LikeModel like = likeService.addLike(userId, productId);
        eventPublisher.publishEvent(new ProductLikeChangedEvent(productId));
        return LikeInfo.from(like);
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        likeService.removeLike(userId, productId);
        eventPublisher.publishEvent(new ProductLikeChangedEvent(productId));
    }

    @Transactional(readOnly = true)
    public Page<LikeInfo> findLikesByUserId(Long userId, Pageable pageable) {
        return likeService.findLikesByUserId(userId, pageable).map(LikeInfo::from);
    }
}
