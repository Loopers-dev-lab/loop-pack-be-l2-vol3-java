package com.loopers.batch.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 배치 전용 Like Repository.
 */
public interface LikeBatchRepository extends JpaRepository<LikeEntity, Long> {

    long countByProductId(Long productId);
}
