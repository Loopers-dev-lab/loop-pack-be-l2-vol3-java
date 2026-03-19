package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.Stock;
import com.loopers.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    // Command

    @Override
    public Stock save(Stock stock) {
        return stockJpaRepository.save(stock);
    }

    @Override
    public int reserveIfAvailable(Long productId, int amount) {
        return stockJpaRepository.reserveIfAvailable(productId, amount);
    }

    @Override
    public int confirmIfReserved(Long productId, int amount) {
        return stockJpaRepository.confirmIfReserved(productId, amount);
    }

    @Override
    public int releaseReservedIfEnough(Long productId, int amount) {
        return stockJpaRepository.releaseReservedIfEnough(productId, amount);
    }

    @Override
    public int releaseConfirmedIfEnough(Long productId, int amount) {
        return stockJpaRepository.releaseConfirmedIfEnough(productId, amount);
    }

    // Query

    @Override
    public Optional<Stock> findByProductId(Long productId) {
        return stockJpaRepository.findByProductId(productId);
    }

    @Override
    public Set<Long> findProductIdsWithReservedStock() {
        return stockJpaRepository.findProductIdsWithReservedStock();
    }
}
