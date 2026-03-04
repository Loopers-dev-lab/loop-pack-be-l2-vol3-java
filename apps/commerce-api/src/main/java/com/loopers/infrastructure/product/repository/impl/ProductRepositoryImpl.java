package com.loopers.infrastructure.product.repository.impl;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        ProductEntity entity = productJpaRepository.save(ProductEntity.toEntity(product));
        return entity.toModel();
    }

    @Override
    public void update(Product product) {
        ProductEntity entity = productJpaRepository.findById(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "등록된 상품이 아닙니다."));

        entity.update(
                product.getName().value(),
                product.getPrice().value(),
                product.getStock().value(),
                product.getDisplayStatus()
        );
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id).map(ProductEntity::toModel);
    }

    @Override
    public Page<Product> findAll(Pageable pageable, Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId, pageable)
                .map(ProductEntity::toModel);
    }

    @Override
    public void deleteById(Long id) {
        productJpaRepository.deleteById(id);
    }

    @Override
    public void deleteByBrandId(Long brandId) {
        productJpaRepository.deleteAllByBrandId(brandId);
    }

    @Override
    public List<Product> findByIds(List<Long> ids) {
        return productJpaRepository.findAllByIdIn(ids).stream()
                .map(ProductEntity::toModel)
                .toList();
    }

    @Override
    public Optional<Product> findByIdWithLock(Long id) {
        return productJpaRepository.findByIdWithLock(id).map(ProductEntity::toModel);
    }
}
