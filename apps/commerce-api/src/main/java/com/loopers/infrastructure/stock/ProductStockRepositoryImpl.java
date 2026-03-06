package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.ProductStock;
import com.loopers.domain.stock.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductStockRepositoryImpl implements ProductStockRepository {

    private final ProductStockJpaRepository productStockJpaRepository;

    @Override
    public ProductStock save(ProductStock productStock) {
        return productStockJpaRepository.save(productStock);
    }

    @Override
    public Optional<ProductStock> findByProductId(Long productId) {
        return productStockJpaRepository.findByProductIdAndDeletedAtIsNull(productId);
    }

    @Override
    public Optional<ProductStock> findByProductIdWithLock(Long productId) {
        return productStockJpaRepository.findByProductIdWithLock(productId);
    }

    @Override
    public Map<Long, ProductStock> findAllByProductIds(Collection<Long> productIds) {
        return productStockJpaRepository.findAllByProductIdInAndDeletedAtIsNull(productIds)
            .stream()
            .collect(Collectors.toMap(ProductStock::getProductId, ps -> ps));
    }

    @Override
    public void deleteByProductId(Long productId) {
        productStockJpaRepository.softDeleteByProductId(productId);
    }

    @Override
    public void deleteAllByProductIds(Collection<Long> productIds) {
        productStockJpaRepository.softDeleteAllByProductIdIn(productIds);
    }

    @Override
    public void deleteAllByBrandId(Long brandId) {
        productStockJpaRepository.softDeleteAllByBrandId(brandId);
    }
}
