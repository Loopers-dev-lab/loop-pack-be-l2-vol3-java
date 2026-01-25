package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductModel createProduct(String name, String description, BigDecimal price, Integer stock) {
        ProductModel product = new ProductModel(name, description, price, stock);
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductModel getProduct(Long id) {
        return productRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + id + "] 상품을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<ProductModel> getProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional
    public ProductModel updateProduct(Long id, String name, String description, BigDecimal price, Integer stock) {
        ProductModel product = getProduct(id);
        product.update(name, description, price, stock);
        return product;
    }

    @Transactional
    public void deleteProduct(Long id) {
        ProductModel product = getProduct(id);
        productRepository.delete(product);
    }
}
