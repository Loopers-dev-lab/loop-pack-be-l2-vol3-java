package com.loopers.domain.stock;

import java.util.Optional;
import java.util.Set;

public interface StockRepository {

    // Command
    Stock save(Stock stock);
    int reserveIfAvailable(Long productId, int amount);
    int confirmIfReserved(Long productId, int amount);
    int releaseReservedIfEnough(Long productId, int amount);
    int releaseConfirmedIfEnough(Long productId, int amount);

    // Query
    Optional<Stock> findByProductId(Long productId);
    Set<Long> findProductIdsWithReservedStock();
}
