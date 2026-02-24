package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Component
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
    public Page<Product> findAll(Pageable pageable) {
        return productJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public Page<Product> findAllByBrandId(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageable);
    }

    @Override
    public boolean existsByBrandIdAndName(Long brandId, String name) {
        return productJpaRepository.existsByBrandIdAndNameAndDeletedAtIsNull(brandId, name);
    }

    @Override
    public boolean existsByBrandIdAndNameAndIdNot(Long brandId, String name, Long id) {
        return productJpaRepository.existsByBrandIdAndNameAndIdNotAndDeletedAtIsNull(brandId, name, id);
    }

    @Override
    public void deleteAllByBrandId(Long brandId) {
        productJpaRepository.softDeleteAllByBrandId(brandId, ZonedDateTime.now());
    }
}
