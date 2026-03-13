package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<ProductModel> findByIdForUpdate(Long id) {
        return productJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Page<ProductModel> findAll(Pageable pageable, Long brandId) {
        if (brandId != null) {
            return productJpaRepository.findAllByDeletedAtIsNullAndBrandId(brandId, pageable);
        }
        return productJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public Page<ProductModel> findAllOrderByLikesDesc(Pageable pageable, Long brandId) {
        if (brandId != null) {
            return productJpaRepository.findAllByBrandIdOrderByLikeCountDesc(brandId, pageable);
        }
        return productJpaRepository.findAllOrderByLikeCountDesc(pageable);
    }
}
