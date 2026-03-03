package com.loopers.infrastructure.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId " +
            "AND o.orderDate >= :startAt AND o.orderDate <= :endAt " +
            "AND o.deletedAt IS NULL")
    Page<OrderEntity> findByUserIdAndOrderDateBetween(
            @Param("userId") UUID userId,
            @Param("startAt") ZonedDateTime startAt,
            @Param("endAt") ZonedDateTime endAt,
            Pageable pageable
    );

    @Query("SELECT o FROM OrderEntity o WHERE o.deletedAt IS NULL")
    Page<OrderEntity> findAllActive(Pageable pageable);

    @Query("SELECT COUNT(i) > 0 FROM OrderItemEntity i WHERE i.productId = :productId")
    boolean existsByProductId(@Param("productId") UUID productId);
}
