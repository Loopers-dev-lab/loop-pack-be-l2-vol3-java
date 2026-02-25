package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    // Command

    @Transactional
    public Product register(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        Product product = Product.create(brandId, name, price, stockQuantity, description);
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long productId, String name, BigDecimal price, Integer stockQuantity, String description) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));

        product.update(name, price, stockQuantity, description);
        return product;
    }

    @Transactional
    public void delete(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다"));
        product.validateNotDeleted();
        product.delete();
    }
}
