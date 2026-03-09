package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<Order> findByIdAndDeletedAtIsNull(@Param("id") Long id);

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.userId = :userId AND o.deletedAt IS NULL",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.userId = :userId AND o.deletedAt IS NULL")
    Page<Order> findAllByUserIdAndDeletedAtIsNull(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.deletedAt IS NULL",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.deletedAt IS NULL")
    Page<Order> findAllByDeletedAtIsNull(Pageable pageable);
}
