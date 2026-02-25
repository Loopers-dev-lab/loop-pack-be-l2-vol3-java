package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
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
}
