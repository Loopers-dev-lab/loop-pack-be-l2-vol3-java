package com.loopers.infrastructure.product;

import com.loopers.domain.product.Option;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OptionJpaRepository extends JpaRepository<Option, Long> {
    List<Option> findByProductIdAndDeletedFalse(Long productId);
    Optional<Option> findByIdAndDeletedFalse(Long id);
    List<Option> findByProductIdInAndDeletedFalse(List<Long> productIds);
    List<Option> findByIdInAndDeletedFalse(List<Long> optionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Option o WHERE o.id = :id AND o.deleted = false")
    Optional<Option> findByIdWithLock(@Param("id") Long id);
}
