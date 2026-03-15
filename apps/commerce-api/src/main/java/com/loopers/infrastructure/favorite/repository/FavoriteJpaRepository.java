package com.loopers.infrastructure.favorite.repository;

import com.loopers.infrastructure.favorite.entity.FavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteJpaRepository extends JpaRepository<FavoriteEntity, Long> {

    Optional<FavoriteEntity> findByMemberIdAndProductId(Long memberId, Long productId);

    List<FavoriteEntity> findByMemberIdAndProductIdIn(Long memberId, List<Long> productIds);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);
}
