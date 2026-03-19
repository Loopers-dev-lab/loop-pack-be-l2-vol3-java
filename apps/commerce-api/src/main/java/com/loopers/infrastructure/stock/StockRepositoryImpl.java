package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.Stock;
import com.loopers.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    // Command

    @Override
    public Stock save(Stock stock) {
        return stockJpaRepository.save(stock);
    }

    // Query

    @Override
    public Optional<Stock> findByProductId(Long productId) {
        return stockJpaRepository.findByProductId(productId);
    }

    @Override
    public Optional<Stock> findByProductIdForUpdate(Long productId) {
        return stockJpaRepository.findByProductIdForUpdate(productId);
    }

    @Override
    public List<Stock> findAllByProductIdInForUpdate(Collection<Long> productIds) {
        return stockJpaRepository.findAllByProductIdInForUpdate(productIds);
    }
}
