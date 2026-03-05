package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface OptionRepository {
    Option save(Option option);
    Optional<Option> findById(Long id);
    Optional<Option> findByIdWithLock(Long id);
    List<Option> findByProductId(Long productId);
    List<Option> findByProductIdIn(List<Long> productIds);
    List<Option> findByIdIn(List<Long> optionIds);
}
