package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → LikeInfo 변환.
 * Controller는 Facade만 호출하며, request는 도메인 파라미터로 변환 후 Service에 전달한다.
 */
@Service
public class LikeFacade {

    private final LikeService likeService;

    public LikeFacade(LikeService likeService) {
        this.likeService = likeService;
    }

    @Transactional
    public LikeInfo addLike(Long userId, Long productId) {
        LikeModel like = likeService.addLike(userId, productId);
        return LikeInfo.from(like);
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        likeService.removeLike(userId, productId);
    }

    @Transactional(readOnly = true)
    public Page<LikeInfo> findLikesByUserId(Long userId, Pageable pageable) {
        return likeService.findLikesByUserId(userId, pageable).map(LikeInfo::from);
    }
}
