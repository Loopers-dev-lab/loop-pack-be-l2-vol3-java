package com.loopers.application.product;

import com.loopers.application.product.command.CreateProductCommand;
import com.loopers.application.product.command.UpdateProductCommand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.query.ProductListCriteria;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class ProductApplicationService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public Product create(CreateProductCommand command) {
        if (brandRepository.findById(command.brandId()).isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "존재하지 않거나 삭제된 브랜드입니다.");
        }
        if (categoryRepository.findById(command.categoryId()).isEmpty()) {
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
    public Product get(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<Product> list(Long brandId, Pageable pageable) {
        return productRepository.findAll(brandId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> list(ProductListCriteria criteria) {
        return productRepository.findAll(criteria.brandId(), criteria.toPageable());
    }

    @Transactional(readOnly = true)
    public Product getIncludingDeleted(Long productId) {
        return productRepository.findByIdIncludingDeleted(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<Product> listIncludingDeleted(Long brandId, Pageable pageable) {
        return productRepository.findAllIncludingDeleted(brandId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> listIncludingDeleted(ProductListCriteria criteria) {
        return productRepository.findAllIncludingDeleted(criteria.brandId(), criteria.toPageable());
    }

    @Transactional(readOnly = true)
    public java.util.List<Long> findActiveProductIdsByBrandId(Long brandId) {
        return productRepository.findIdsByBrandId(brandId);
    }

    @Transactional
    public void deleteSoftByBrandId(Long brandId) {
        productRepository.softDeleteByBrandId(brandId);
    }

    @Transactional
    public Product update(Long productId, UpdateProductCommand command) {
        Product existing = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        if (command.brandId() != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드는 수정할 수 없습니다.");
        }

        if (categoryRepository.findById(command.categoryId()).isEmpty()) {
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
    public void deleteSoft(Long productId) {
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
