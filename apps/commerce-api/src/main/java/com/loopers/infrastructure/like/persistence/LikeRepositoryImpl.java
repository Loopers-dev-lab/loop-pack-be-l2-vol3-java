package com.loopers.infrastructure.like.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link LikeRepository}의 인프라스트럭처 구현체.
 *
 * <p>{@link LikeJpaRepository}에 위임하여 좋아요 영속성을 처리한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public Slice<Like> findAllByUserId(Long userId, Pageable pageable) {
        return likeJpaRepository.findAllByUserId(userId, pageable);
    }

    @Override
    public List<Long> findProductIdsByUserIdAndProductIdIn(Long userId, List<Long> productIds) {
        return likeJpaRepository.findProductIdsByUserIdAndProductIdIn(userId, productIds);
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public void delete(Like like) {
        likeJpaRepository.delete(like);
    }

    @Override
    public void deleteAllByProductId(Long productId) {
        likeJpaRepository.deleteAllByProductId(productId);
    }

    @Override
    public void deleteAllByProductIdIn(List<Long> productIds) {
        likeJpaRepository.deleteAllByProductIdIn(productIds);
    }
}
