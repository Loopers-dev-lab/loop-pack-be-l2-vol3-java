package com.loopers.infrastructure.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LikeJpaRepository extends JpaRepository<LikeEntity, UUID> {

    boolean existsByMemberIdAndProductId(String memberId, UUID productId);

    void deleteByMemberIdAndProductId(String memberId, UUID productId);

    void deleteByProductIdIn(List<UUID> productIds);

    Page<LikeEntity> findByMemberIdOrderByCreatedAtDesc(String memberId, Pageable pageable);
}
