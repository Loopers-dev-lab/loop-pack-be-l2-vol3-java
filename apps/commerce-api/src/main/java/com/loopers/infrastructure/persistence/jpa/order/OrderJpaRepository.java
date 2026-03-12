package com.loopers.infrastructure.persistence.jpa.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Order JPA Repository.
 * Spring Data JPA 인터페이스.
 */
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    @Query("SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<OrderJpaEntity> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT o FROM OrderJpaEntity o LEFT JOIN FETCH o.items WHERE o.id = :id AND o.userId = :userId")
    Optional<OrderJpaEntity> findByIdAndUserIdWithItems(@Param("id") Long id, @Param("userId") Long userId);

    Page<OrderJpaEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);
}
