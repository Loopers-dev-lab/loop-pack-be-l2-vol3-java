package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.CreateProductRequest;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public List<ProductInfo> createProducts(CreateProductCommand command) {
        command.products().keySet().forEach(brandService::getById);
        
        Map<Long, CreateProductRequest> domainRequest = new HashMap<>();
        command.products().forEach((brandId, item) -> {
            domainRequest.put(brandId, new CreateProductRequest(
                item.name(),
                item.price(),
                item.stock()
            ));
        });
        
        List<Product> products = productService.createProducts(domainRequest);
        return products.stream()
            .map(product -> {
                Brand brand = brandService.getById(product.getRefBrandId());
                return ProductInfo.of(product, brand);
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(Long productId) {
        Product product = productService.getById(productId);
        Brand brand = brandService.getById(product.getRefBrandId());
        return ProductInfo.of(product, brand);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(ProductSearchCommand command) {
        if (command.hasBrandId()) {
            brandService.getById(command.brandId());
        }
        
        ProductSearchCondition condition = new ProductSearchCondition(
            command.brandId(),
            command.sortType(),
            command.page(),
            command.size()
        );
        
        return productService.findProducts(condition).stream()
            .map(product -> {
                Brand brand = brandService.getById(product.getRefBrandId());
                return ProductInfo.of(product, brand);
            })
            .toList();
    }
}
