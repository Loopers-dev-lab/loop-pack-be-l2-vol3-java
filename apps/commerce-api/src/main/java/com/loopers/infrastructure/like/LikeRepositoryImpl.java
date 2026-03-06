package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    // Command

    @Override
    public Like save(Like like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public int deleteByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    // Query

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public Page<Like> findAllByUserIdWithActiveProduct(Long userId, Pageable pageable) {
        return likeJpaRepository.findAllByUserIdWithActiveProduct(userId, pageable);
    }
}
