package com.loopers.infrastructure.product;

import com.loopers.domain.product.Option;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OptionJpaRepository extends JpaRepository<Option, Long> {
    List<Option> findByProductIdAndDeletedFalse(Long productId);
    Optional<Option> findByIdAndDeletedFalse(Long id);
    List<Option> findByProductIdInAndDeletedFalse(List<Long> productIds);
    List<Option> findByIdInAndDeletedFalse(List<Long> optionIds);
}
