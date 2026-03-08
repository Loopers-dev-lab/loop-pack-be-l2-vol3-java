package com.loopers.domain.stock;

import com.loopers.domain.product.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.Map;

@RequiredArgsConstructor
public class ProductStockDomainService {

    private final ProductStockRepository productStockRepository;

    public ProductStock create(Long productId, int quantity) {
        return productStockRepository.save(new ProductStock(productId, new Stock(quantity)));
    }

    public ProductStock getByProductId(Long productId) {
        return productStockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다."));
    }

    public Map<Long, ProductStock> getByProductIds(Collection<Long> ids) {
        return productStockRepository.findAllByProductIds(ids);
    }

    public ProductStock deductWithLock(Long productId, int quantity) {
        ProductStock productStock = productStockRepository.findByProductIdWithLock(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다."));
        productStock.deduct(quantity);
        return productStockRepository.save(productStock);
    }

    public ProductStock restoreWithLock(Long productId, int quantity) {
        ProductStock productStock = productStockRepository.findByProductIdWithLock(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다."));
        productStock.restore(quantity);
        return productStockRepository.save(productStock);
    }

    public ProductStock changeQuantity(Long productId, int quantity) {
        ProductStock productStock = getByProductId(productId);
        productStock.changeQuantity(new Stock(quantity));
        return productStockRepository.save(productStock);
    }

    public void deleteByProductId(Long productId) {
        productStockRepository.deleteByProductId(productId);
    }

    public void deleteAllByProductIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        productStockRepository.deleteAllByProductIds(productIds);
    }

    public void deleteAllByBrandId(Long brandId) {
        productStockRepository.deleteAllByBrandId(brandId);
    }
}
