package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    public LikeModel save(LikeModel like) {
        return likeRepository.save(like);
    }

    public Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId);
    }

    public Optional<LikeModel> findActiveLike(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductIdAndDeletedAtIsNull(userId, productId);
    }

    public Page<LikeModel> getMyLikes(Long userId, Pageable pageable) {
        return likeRepository.findActiveLikesWithActiveProduct(userId, pageable);
    }
}
