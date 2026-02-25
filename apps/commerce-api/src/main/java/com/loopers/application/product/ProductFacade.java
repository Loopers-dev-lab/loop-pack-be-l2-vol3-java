package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    // Command

    @Transactional
    public ProductInfo register(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        Brand brand = brandService.getActiveBrand(brandId);
        Product product = productService.register(brandId, name, price, stockQuantity, description);
        return ProductInfo.from(product, brand.getName());
    }

    @Transactional
    public ProductInfo update(Long productId, String name, BigDecimal price, Integer stockQuantity, String description) {
        Product product = productService.update(productId, name, price, stockQuantity, description);
        Brand brand = brandService.getBrand(product.getBrandId());
        return ProductInfo.from(product, brand.getName());
    }

    @Transactional
    public void delete(Long productId) {
        productService.delete(productId);
    }

    // Query

    public Page<ProductInfo> getList(String name, Long brandId, Boolean deleted, Pageable pageable) {
        Page<Product> products = productService.findProducts(name, brandId, deleted, pageable);
        return products.map(product -> {
            Brand brand = brandService.getBrand(product.getBrandId());
            return ProductInfo.from(product, brand.getName());
        });
    }
}
