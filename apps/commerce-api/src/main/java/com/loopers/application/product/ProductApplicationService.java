package com.loopers.application.product;

import com.loopers.application.product.command.CreateProductCommand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductApplicationService {

    private final ProductRepository productRepository;

    @Transactional
    public Product create(CreateProductCommand command) {
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
}
