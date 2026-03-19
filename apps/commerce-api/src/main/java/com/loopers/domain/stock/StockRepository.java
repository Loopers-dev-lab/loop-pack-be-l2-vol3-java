package com.loopers.domain.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StockRepository {

    // Command
    Stock save(Stock stock);

    // Query
    Optional<Stock> findByProductId(Long productId);
    Optional<Stock> findByProductIdForUpdate(Long productId);
    List<Stock> findAllByProductIdInForUpdate(Collection<Long> productIds);
    Set<Long> findProductIdsWithReservedStock();
}
