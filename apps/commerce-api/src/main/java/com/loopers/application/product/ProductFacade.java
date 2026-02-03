package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class ProductFacade {
    private final ProductService productService;

    public ProductInfo createProduct(String name, String description, BigDecimal price, Integer stock) {
        ProductModel product = productService.createProduct(name, description, price, stock);
        return ProductInfo.from(product);
    }

    public ProductInfo getProduct(Long id) {
        ProductModel product = productService.getProduct(id);
        return ProductInfo.from(product);
    }

    public Page<ProductInfo> getProducts(Pageable pageable) {
        Page<ProductModel> products = productService.getProducts(pageable);
        return products.map(ProductInfo::from);
    }

    public ProductInfo updateProduct(Long id, String name, String description, BigDecimal price, Integer stock) {
        ProductModel product = productService.updateProduct(id, name, description, price, stock);
        return ProductInfo.from(product);
    }

    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
