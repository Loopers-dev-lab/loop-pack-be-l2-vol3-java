package com.loopers.domain.stock;

import java.util.List;
import java.util.Optional;

public interface StockRepository {
    Optional<StockModel> findByProductId(Long productId);

    Optional<StockModel> findByProductIdForUpdate(Long productId);

    List<StockModel> findAllByProductIdIn(List<Long> productIds);
}
