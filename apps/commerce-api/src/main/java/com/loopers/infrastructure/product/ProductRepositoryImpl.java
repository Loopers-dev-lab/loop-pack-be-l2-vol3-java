package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductPageReadCache;
import com.loopers.application.product.ProductReadCache;
import com.loopers.domain.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final ProductReadCache productReadCache;
    private final ProductPageReadCache productPageReadCache;

    @Override
    public Product save(Product product) {
        Product saved = productJpaRepository.save(product);
        evictAfterCommit(saved.getId());
        return saved;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Product> findAllByIds(Collection<Long> ids) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public PageResult<Product> findAll(Long brandId, ProductSortType sort, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, toSort(sort));

        Page<Product> result;
        if (brandId != null) {
            result = productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageRequest);
        } else {
            result = productJpaRepository.findAllByDeletedAtIsNull(pageRequest);
        }

        return new PageResult<>(
            result.getContent(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Override
    public void softDeleteAllByBrandId(Long brandId) {
        productJpaRepository.softDeleteAllByBrandId(brandId);
        evictAllAfterCommit();
    }

    @Override
    public int incrementLikeCount(Long id) {
        int updated = productJpaRepository.incrementLikeCount(id);
        evictAfterCommit(id);
        return updated;
    }

    @Override
    public int decrementLikeCount(Long id) {
        int updated = productJpaRepository.decrementLikeCount(id);
        evictAfterCommit(id);
        return updated;
    }

    private void evictAfterCommit(Long id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        productReadCache.evict(id);
                        productPageReadCache.evictAll();
                    }
                }
            );
        } else {
            productReadCache.evict(id);
            productPageReadCache.evictAll();
        }
    }

    private void evictAllAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        productReadCache.evictAll();
                        productPageReadCache.evictAll();
                    }
                }
            );
        } else {
            productReadCache.evictAll();
            productPageReadCache.evictAll();
        }
    }

    private Sort toSort(ProductSortType sortType) {
        return switch (sortType) {
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
