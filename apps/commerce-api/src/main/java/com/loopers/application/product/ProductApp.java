package com.loopers.application.product;

import com.loopers.domain.common.cursor.CursorPageResult;
import com.loopers.domain.product.ProductMetricsModel;
import com.loopers.domain.product.ProductMetricsRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class ProductApp {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductCacheStore productCacheStore;
    private final ProductMetricsRepository productMetricsRepository;

    @Transactional
    public ProductInfo createProduct(String productId, String brandId, String productName, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.createProduct(productId, brandId, productName, price, stockQuantity);
        return ProductInfo.from(product);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProduct(String productId) {
        return productCacheStore.get(productId).orElseGet(() -> {
            ProductModel product = productRepository.findByProductId(new ProductId(productId))
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));
            ProductInfo info = ProductInfo.from(product);
            productCacheStore.put(productId, info);
            return info;
        });
    }

    @Caching(evict = {
        @CacheEvict(value = "product",   key = "#productId"),
        @CacheEvict(value = "products", allEntries = true)
    })
    @Transactional
    public ProductInfo updateProduct(String productId, String productName, BigDecimal price, int stockQuantity) {
        ProductModel product = productService.updateProduct(productId, productName, price, stockQuantity);
        return ProductInfo.from(product);
    }

    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#productId"),
        @CacheEvict(value = "products", allEntries = true)
    })
    @Transactional
    public void deleteProduct(String productId) {
        productService.deleteProduct(productId);
    }

    @Cacheable(value = "products", key = "#brandId + ':' + #sortBy + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<ProductInfo> getProducts(String brandId, String sortBy, Pageable pageable) {
        return productService.getProducts(brandId, sortBy, pageable).map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    public CursorPageResult<ProductInfo> getProductsByCursor(String brandId, String sortBy, String cursor, int size) {
        Long refBrandId = productService.resolveRefBrandId(brandId);
        return productRepository.findProductsByCursor(refBrandId, sortBy, cursor, size).map(ProductInfo::from);
    }

    @Transactional(readOnly = true)
    public ProductInfo getProductByRefId(Long id) {
        ProductModel product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 상품이 존재하지 않습니다."));
        return ProductInfo.from(product);
    }

    @Transactional
    public void deleteProductsByBrandRefId(Long brandId) {
        productService.deleteProductsByBrandRefId(brandId);
    }

    @Transactional(readOnly = true)
    public long getLikesCount(Long refProductId) {
        return productMetricsRepository.findByRefProductId(refProductId)
                .map(ProductMetricsModel::getLikeCount)
                .orElse(0L);
    }
}
