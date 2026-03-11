package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        LikeEntity likeEntity = LikeEntity.toEntity(like);

        return LikeEntity.toDomain(likeJpaRepository.save(likeEntity));
    }

    @Override
    public void delete(Like like) {
        likeJpaRepository.findByRefProductIdAndRefUserId(like.getRefProductId(), like.getRefUserId())
            .ifPresent(likeJpaRepository::delete);
    }

    @Override
    public boolean existByUniqueId(Long productId, Long userId) {
        return likeJpaRepository.existsByRefProductIdAndRefUserId(productId, userId);
    }

    @Override
    public Optional<Like> findByUniqueId(Long productId, Long userId) {
        return likeJpaRepository.findByRefProductIdAndRefUserId(productId, userId)
            .map(LikeEntity::toDomain);
    }
}
