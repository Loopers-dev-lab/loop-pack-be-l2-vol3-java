package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public List<Like> findAllByUserId(Long userId) {
        return likeJpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public void deleteByUserIdAndProductId(Long userId, Long productId) {
        likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public void deleteAllByProductId(Long productId) {
        likeJpaRepository.deleteAllByProductId(productId);
    }

    @Override
    public void deleteAllByProductIds(List<Long> productIds) {
        likeJpaRepository.deleteAllByProductIdIn(productIds);
    }
}
