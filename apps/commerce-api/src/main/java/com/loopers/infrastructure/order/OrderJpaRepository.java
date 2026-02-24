package com.loopers.infrastructure.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<OrderEntity> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId " +
            "AND o.createdAt >= :startAt AND o.createdAt < :endAt " +
            "ORDER BY o.createdAt DESC")
    List<OrderEntity> findAllByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("startAt") ZonedDateTime startAt,
            @Param("endAt") ZonedDateTime endAt);
}
