package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductModel save(ProductModel productModel) {
        return productJpaRepository.save(productModel);
    }

    @Override
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<ProductModel> findAll(Pageable pageable) {
        return productJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public List<ProductModel> findAll() {
        return productJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageable);
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public List<ProductModel> findAllByIdIn(List<Long> ids) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public Page<ProductModel> findAllSortedByLikeCountDesc(Pageable pageable) {
        return productJpaRepository.findAllSortedByMetricsLikeCountDesc(pageable);
    }

    @Override
    public Page<ProductModel> findAllByBrandIdSortedByLikeCountDesc(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandIdSortedByMetricsLikeCountDesc(brandId, pageable);
    }

    @Override
    public Map<Long, Long> findLikeCountsByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) return Collections.emptyMap();
        return productJpaRepository.findLikeCountsByProductIds(productIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()));
    }

    @Override
    public long findLikeCountByProductId(Long productId) {
        return productJpaRepository.findLikeCountByProductId(productId);
    }

    @Override
    public int decreaseStock(Long id, int quantity) {
        return productJpaRepository.decreaseStock(id, quantity);
    }

    @Override
    public int increaseStock(Long id, int quantity) {
        return productJpaRepository.increaseStock(id, quantity);
    }

    @Override
    public List<ProductModel> findByIdModulo(int divisor, int remainder) {
        return productJpaRepository.findByIdModulo(divisor, remainder);
    }
}
