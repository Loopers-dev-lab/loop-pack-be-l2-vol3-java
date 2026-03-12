package com.loopers.domain.like;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 좋아요 Repository 인터페이스.
 * 순수 Java 인터페이스로 Spring/JPA 의존성 없음.
 * 구현체는 Infrastructure Layer에 위치.
 */
public interface LikeRepository {

    Like save(Like like);

    void delete(Like like);

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    boolean exists(Long userId, Long productId);

    long countByProductId(Long productId);

    Map<Long, Long> countByProductIds(List<Long> productIds);
}
