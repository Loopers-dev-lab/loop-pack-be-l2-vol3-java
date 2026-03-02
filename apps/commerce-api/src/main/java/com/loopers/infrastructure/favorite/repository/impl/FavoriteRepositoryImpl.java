package com.loopers.infrastructure.favorite.repository.impl;

import com.loopers.domain.favorite.model.Favorite;
import com.loopers.domain.favorite.repository.FavoriteRepository;
import com.loopers.infrastructure.favorite.entity.FavoriteEntity;
import com.loopers.infrastructure.favorite.repository.FavoriteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class FavoriteRepositoryImpl implements FavoriteRepository {

    private final FavoriteJpaRepository favoriteJpaRepository;

    @Override
    public void save(Favorite favorite) {
        FavoriteEntity entity = FavoriteEntity.toEntity(favorite);
        favoriteJpaRepository.save(entity);
    }

    @Override
    public Optional<Favorite> findByMemberIdAndProductId(Long memberId, Long productId) {
        return favoriteJpaRepository.findByMemberIdAndProductId(memberId, productId)
                .map(FavoriteEntity::toModel);
    }

    @Override
    public void delete(Favorite favorite) {
        favoriteJpaRepository.deleteById(favorite.getId());
    }

    @Override
    public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
        return favoriteJpaRepository.existsByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public long countByProductId(Long productId) {
        return favoriteJpaRepository.countByProductId(productId);
    }
}
