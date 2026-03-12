package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public Optional<LikeModel> findByUserIdAndProductIdAndDeletedAtIsNull(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductIdAndDeletedAtIsNull(userId, productId);
    }

    @Override
    public Page<LikeModel> findActiveLikesWithActiveProduct(Long userId, Pageable pageable) {
        return likeJpaRepository.findActiveLikesWithActiveProduct(userId, pageable);
    }
}
