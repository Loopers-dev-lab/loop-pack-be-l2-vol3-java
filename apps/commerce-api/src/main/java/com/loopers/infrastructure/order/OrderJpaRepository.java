package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    List<OrderModel> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderModel o WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<OrderModel> findByIdWithLockAndDeletedAtIsNull(Long id);

    Page<OrderModel> findAll(Pageable pageable);

    boolean existsByUserIdAndStatus(Long userId, OrderStatus status);
}
