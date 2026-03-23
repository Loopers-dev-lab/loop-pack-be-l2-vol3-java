package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.CreateProductRequest;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.product.ProductCacheStore;
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
    private final ProductCacheStore productCacheStore;

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
        return productCacheStore.getProduct(productId)
            .orElseGet(() -> {
                Product product = productService.getById(productId);
                Brand brand = brandService.getById(product.getRefBrandId());
                ProductInfo info = ProductInfo.of(product, brand);
                productCacheStore.putProduct(productId, info);
                return info;
            });
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getProducts(ProductSearchCommand command) {
        return productCacheStore.getProducts(command)
            .orElseGet(() -> {
                if (command.hasBrandId()) {
                    brandService.getById(command.brandId());
                }

                ProductSearchCondition condition = new ProductSearchCondition(
                    command.brandId(),
                    command.sortType(),
                    command.page(),
                    command.size()
                );

                List<ProductInfo> list = productService.findProducts(condition).stream()
                    .map(product -> {
                        Brand brand = brandService.getById(product.getRefBrandId());
                        return ProductInfo.of(product, brand);
                    })
                    .toList();
                productCacheStore.putProducts(command, list);
                return list;
            });
    }
}
