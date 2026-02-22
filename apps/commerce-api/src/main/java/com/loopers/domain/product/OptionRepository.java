package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

public interface OptionRepository {
    Option save(Option option);
    Optional<Option> findById(Long id);
    List<Option> findByProductId(Long productId);
    void deleteByProductId(Long productId);
}
