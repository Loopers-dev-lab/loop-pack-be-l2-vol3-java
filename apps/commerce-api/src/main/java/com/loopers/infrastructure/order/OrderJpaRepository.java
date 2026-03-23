package com.loopers.infrastructure.order;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    @Query("SELECT o FROM OrderEntity o " +
           "LEFT JOIN FETCH o.items " +
           "LEFT JOIN FETCH o.histories " +
           "WHERE o.id = :id")
    Optional<OrderEntity> findByIdWithDetails(@Param("id") Long id);
}
