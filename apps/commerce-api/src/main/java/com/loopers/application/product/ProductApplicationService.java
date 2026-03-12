package com.loopers.application.product;

import com.loopers.application.brand.BrandCacheRepository;
import com.loopers.application.coupon.category.CategoryCacheRepository;
import com.loopers.application.product.command.CreateProductCommand;
import com.loopers.application.product.command.UpdateProductCommand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.query.ProductListCriteria;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final BrandCacheRepository brandCacheRepository;
    private final CategoryCacheRepository categoryCacheRepository;

    @Transactional
    public Product create(CreateProductCommand command) {
        if (!brandCacheRepository.existsById(command.brandId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "존재하지 않거나 삭제된 브랜드입니다.");
        }
        if (!categoryCacheRepository.existsById(command.categoryId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "존재하지 않거나 삭제된 카테고리입니다.");
        }

        Product product = new Product(
                command.name(),
                command.price(),
                command.stock(),
                command.description(),
                command.categoryId(),
                command.brandId()
        );
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product get(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<Product> list(ProductListCriteria criteria) {
        return productRepository.search(criteria);
    }

    @Transactional(readOnly = true)
    public Product getIncludingDeleted(UUID productId) {
        return productRepository.findByIdIncludingDeleted(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<Product> listIncludingDeleted(ProductListCriteria criteria) {
        return productRepository.findAllIncludingDeleted(criteria);
    }

    @Transactional(readOnly = true)
    public java.util.List<UUID> findActiveProductIdsByBrandId(UUID brandId) {
        return productRepository.findIdsByBrandId(brandId);
    }

    @Transactional
    public void deleteSoftByBrandId(UUID brandId) {
        productRepository.softDeleteByBrandId(brandId);
    }

    @Transactional
    public Product update(UUID productId, UpdateProductCommand command) {
        Product existing = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        if (command.brandId() != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드는 수정할 수 없습니다.");
        }

        if (!categoryCacheRepository.existsById(command.categoryId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "존재하지 않거나 삭제된 카테고리입니다.");
        }

        Product updated = new Product(
                existing.id(),
                command.name(),
                command.price(),
                command.stock(),
                command.description(),
                command.categoryId(),
                existing.brandId(),
                existing.likeCount(),
                existing.deletedAt()
        );
        return productRepository.save(updated);
    }

    @Transactional
    public void deleteSoft(UUID productId) {
        Product existing = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        Product deleted = new Product(
                existing.id(),
                existing.name(),
                existing.price(),
                existing.stock(),
                existing.description(),
                existing.categoryId(),
                existing.brandId(),
                existing.likeCount(),
                ZonedDateTime.now()
        );
        productRepository.save(deleted);
    }
}
