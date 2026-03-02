package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public Optional<Product> findActiveById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<Product> findAllActive(Pageable pageable) {
        return productJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public Page<Product> findAllActiveByBrandId(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageable);
    }

    @Override
    public List<Product> findAllActiveByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public List<Product> findAllActiveByIdIn(List<Long> ids) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public List<Long> findAllActiveIdsByBrandId(Long brandId) {
        return productJpaRepository.findAllIdsByBrandIdAndDeletedAtIsNull(brandId);
    }
}
