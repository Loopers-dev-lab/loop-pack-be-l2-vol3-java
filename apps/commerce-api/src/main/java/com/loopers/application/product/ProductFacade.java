package com.loopers.application.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.OutboxEventHelper;
import com.loopers.domain.brand.Brand;
import java.time.ZonedDateTime;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
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
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

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

    public void recordView(Long productId) {
        outboxEventRepository.save(OutboxEvent.create(
            "catalog-events",
            OutboxEventHelper.toJson(objectMapper, Map.of("type", "PRODUCT_VIEWED", "productId", productId, "occurredAt", ZonedDateTime.now().toString())),
            String.valueOf(productId)
        ));
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
