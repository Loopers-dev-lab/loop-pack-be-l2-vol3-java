package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
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
}
