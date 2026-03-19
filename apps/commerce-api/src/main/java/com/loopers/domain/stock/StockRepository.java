package com.loopers.domain.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository {

    // Command
    Stock save(Stock stock);

    // Query
    Optional<Stock> findByProductId(Long productId);
    Optional<Stock> findByProductIdForUpdate(Long productId);
    List<Stock> findAllByProductIdInForUpdate(Collection<Long> productIds);
}
