package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    @Query(
        value = "SELECT o FROM OrderModel o WHERE o.deletedAt IS NULL",
        countQuery = "SELECT COUNT(o) FROM OrderModel o WHERE o.deletedAt IS NULL"
    )
    Page<OrderModel> findAllByDeletedAtIsNull(Pageable pageable);

    @Query("""
        SELECT o
        FROM OrderModel o
        WHERE o.userId = :userId
          AND o.deletedAt IS NULL
          AND o.createdAt >= :startAt
          AND o.createdAt < :endAt
        ORDER BY o.createdAt DESC
        """)
    List<OrderModel> findAllByUserIdAndPeriod(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    @EntityGraph(attributePaths = "orderItems")
    @Query("SELECT o FROM OrderModel o WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<OrderModel> findDetailById(Long id);

    @EntityGraph(attributePaths = "orderItems")
    @Query("SELECT o FROM OrderModel o WHERE o.id = :id AND o.userId = :userId AND o.deletedAt IS NULL")
    Optional<OrderModel> findDetailByIdAndUserId(Long id, Long userId);
}
