package com.loopers.domain.favorite.repository;

import com.loopers.domain.favorite.model.Favorite;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository {

    void save(Favorite favorite);

    Optional<Favorite> findByMemberIdAndProductId(Long memberId, Long productId);

    List<Favorite> findByMemberIdAndProductIds(Long memberId, List<Long> productIds);

    void delete(Favorite favorite);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);
}
