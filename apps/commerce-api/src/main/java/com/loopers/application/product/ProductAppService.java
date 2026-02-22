package com.loopers.application.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductAppService {
    private final ProductRepository productRepository;

    @Transactional
    public Product create(Long brandId, String name, Money price, int stock) {
        Product product = Product.create(brandId, name, price, stock);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional
    public Product decreaseStock(Long productId, int quantity) {
        Product product = getById(productId);
        product.decreaseStock(quantity);
        return productRepository.save(product);
    }

    @Transactional
    public Product increaseStock(Long productId, int quantity) {
        Product product = getById(productId);
        product.increaseStock(quantity);
        return productRepository.save(product);
    }
}
