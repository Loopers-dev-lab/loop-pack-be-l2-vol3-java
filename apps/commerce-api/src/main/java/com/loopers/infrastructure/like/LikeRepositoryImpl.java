package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {
    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        LikeJpaEntity entity = LikeJpaEntity.from(like);
        LikeJpaEntity saved = likeJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public void delete(Like like) {
        likeJpaRepository.findByUserIdAndProductId(like.getUserId(), like.getProductId())
                .ifPresent(likeJpaRepository::delete);
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId)
                .map(LikeJpaEntity::toDomain);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public List<Like> findByUserId(Long userId) {
        return likeJpaRepository.findByUserId(userId).stream()
                .map(LikeJpaEntity::toDomain)
                .toList();
    }
}
