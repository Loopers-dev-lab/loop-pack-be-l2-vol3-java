package com.loopers.infrastructure.like.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;

import lombok.RequiredArgsConstructor;

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

    @Override
    public Map<Long, Long> countByProductIdIn(List<Long> productIds) {
        return likeJpaRepository.countByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }
}
