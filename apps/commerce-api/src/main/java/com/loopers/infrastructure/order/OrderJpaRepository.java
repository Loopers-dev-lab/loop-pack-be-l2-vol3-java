package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    @Query("SELECT o FROM OrderModel o LEFT JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<OrderModel> findByIdWithOrderItems(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT o FROM OrderModel o LEFT JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<OrderModel> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT o FROM OrderModel o LEFT JOIN FETCH o.orderItems WHERE o.id = :id AND o.userId = :userId")
    Optional<OrderModel> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /** orderedAt >= start and orderedAt < end, orderedAt DESC */
    Page<OrderModel> findByUserIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtDesc(
            Long userId,
            ZonedDateTime start,
            ZonedDateTime end,
            Pageable pageable
    );

    /** 어드민용: userId 없이 orderedAt 구간·페이징 조회. orderedAt >= start and orderedAt < end, orderedAt DESC */
    Page<OrderModel> findByOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtDesc(
            ZonedDateTime start,
            ZonedDateTime end,
            Pageable pageable
    );
}
