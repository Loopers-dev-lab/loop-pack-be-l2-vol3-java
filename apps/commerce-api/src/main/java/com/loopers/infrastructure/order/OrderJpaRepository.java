package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    // Query
    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    @Query(value = "SELECT o FROM Order o WHERE o.userId = :userId "
                 + "AND (:startDate IS NULL OR o.createdAt >= :startDate) "
                 + "AND (:endDate IS NULL OR o.createdAt < :endDate) "
                 + "ORDER BY o.createdAt DESC",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.userId = :userId "
                      + "AND (:startDate IS NULL OR o.createdAt >= :startDate) "
                      + "AND (:endDate IS NULL OR o.createdAt < :endDate)")
    Page<Order> findAllByUserIdAndCreatedAtBetween(@Param("userId") Long userId,
                                                   @Param("startDate") ZonedDateTime startDate,
                                                   @Param("endDate") ZonedDateTime endDate,
                                                   Pageable pageable);
}
