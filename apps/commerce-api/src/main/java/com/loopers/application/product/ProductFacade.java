package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ProductFacade {
    private final BrandService brandService;
    private final ProductService productService;

    public ProductInfo register(ProductCreateCommand command) {
        brandService.getBrand(command.brandId());
        return productService.register(command);
    }

    public ProductInfo getProduct(Long id) {
        return productService.getProduct(id);
    }

    public Page<ProductInfo> getAllProducts(Long brandId, Pageable pageable) {
        return productService.getAllProducts(brandId, pageable);
    }

    public ProductInfo update(Long id, ProductUpdateCommand command) {
        return productService.update(id, command);
    }

    public void delete(Long id) {
        productService.delete(id);
    }
}
