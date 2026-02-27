package com.loopers.domain.favorite.repository;

import com.loopers.domain.favorite.model.Favorite;

import java.util.Optional;

public interface FavoriteRepository {

    void save(Favorite favorite);

    Optional<Favorite> findByMemberIdAndProductId(Long memberId, Long productId);

    void delete(Favorite favorite);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);
}
