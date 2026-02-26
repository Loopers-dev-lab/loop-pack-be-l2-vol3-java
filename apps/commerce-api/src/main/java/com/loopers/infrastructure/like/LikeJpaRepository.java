package com.loopers.infrastructure.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LikeJpaRepository extends JpaRepository<LikeEntity, Long> {

    boolean existsByMemberIdAndProductId(String memberId, Long productId);

    void deleteByMemberIdAndProductId(String memberId, Long productId);

    Page<LikeEntity> findByMemberIdOrderByCreatedAtDesc(String memberId, Pageable pageable);
}
