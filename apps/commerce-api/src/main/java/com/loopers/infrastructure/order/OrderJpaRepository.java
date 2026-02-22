package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query("SELECT o FROM Order o WHERE o.userId = :userId " +
            "AND o.createdAt >= :startAt AND o.createdAt < :endAt " +
            "ORDER BY o.createdAt DESC")
    List<Order> findAllByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("startAt") ZonedDateTime startAt,
            @Param("endAt") ZonedDateTime endAt);
}
