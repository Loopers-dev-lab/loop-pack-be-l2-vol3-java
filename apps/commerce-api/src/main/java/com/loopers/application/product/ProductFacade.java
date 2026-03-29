package com.loopers.application.product;

import com.loopers.application.product.event.ProductPriceChangedEvent;
import com.loopers.application.product.event.ProductViewedEvent;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.exception.ProductInsufficientStockException;
import com.loopers.domain.product.exception.ProductNotDeletedException;
import com.loopers.infrastructure.product.ProductCacheStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final ProductService productService;
    private final ProductCacheStore productCacheStore;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProductDto.ProductInfo register(Long brandId, String name, String description, BigDecimal price, Integer stock) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

        String normalizedName = normalizeName(name);
        if (productRepository.existsByName(normalizedName)) {
            throw new CoreException(ErrorType.DUPLICATE_PRODUCT_NAME);
        }

        Product product = productService.createProduct(brandId, normalizedName, description, price, stock);
        Product saved = productRepository.save(product);
        return ProductDto.ProductInfo.of(saved, brand.getName());
    }

    @Transactional
    public ProductDto.ProductInfo update(Long productId, String name, String description, BigDecimal price, Integer stock) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

        String normalizedName = normalizeName(name);
        if (!product.getName().equals(normalizedName) && productRepository.existsByName(normalizedName)) {
            throw new CoreException(ErrorType.DUPLICATE_PRODUCT_NAME);
        }

        BigDecimal oldPrice = product.getPrice();
        productService.updateProduct(product, normalizedName, description, price, stock);
        Product saved = productRepository.save(product);
        Brand brand = brandRepository.findById(saved.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        productCacheStore.evictProduct(productId);

        if (price != null && price.compareTo(oldPrice) != 0) {
            eventPublisher.publishEvent(new ProductPriceChangedEvent(productId, price, Instant.now()));
        }

        return ProductDto.ProductInfo.of(saved, brand.getName());
    }

    @Transactional(readOnly = true)
    public Page<ProductDto.ProductInfo> getProducts(ProductSortType sortType, Pageable pageable) {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();

        return productCacheStore.getProductList(null, sortType, page, size)
            .orElseGet(() -> {
                Pageable sorted = PageRequest.of(page, size, sortType.toSort());
                Page<Product> products = productRepository.findAll(sorted);

                Set<Long> brandIds = products.stream()
                    .map(Product::getBrandId)
                    .collect(Collectors.toSet());

                Map<Long, String> brandNameMap = brandRepository.findAllByIds(brandIds).stream()
                    .collect(Collectors.toMap(Brand::getId, Brand::getName));

                Page<ProductDto.ProductInfo> result = products.map(product ->
                    ProductDto.ProductInfo.of(product, brandNameMap.getOrDefault(product.getBrandId(), ""))
                );
                productCacheStore.putProductList(null, sortType, page, size, result);
                return result;
            });
    }

    @Transactional(readOnly = true)
    public Page<ProductDto.ProductInfo> getProductsByBrand(Long brandId, ProductSortType sortType, Pageable pageable) {
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();

        return productCacheStore.getProductList(brandId, sortType, page, size)
            .orElseGet(() -> {
                Brand brand = brandRepository.findById(brandId)
                    .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

                Pageable sorted = PageRequest.of(page, size, sortType.toSort());
                Page<ProductDto.ProductInfo> result = productRepository.findByBrandId(brandId, sorted)
                    .map(product -> ProductDto.ProductInfo.of(product, brand.getName()));
                productCacheStore.putProductList(brandId, sortType, page, size, result);
                return result;
            });
    }

    @Transactional(readOnly = true)
    public ProductDto.ProductInfo getProduct(Long productId) {
        ProductDto.ProductInfo info = productCacheStore.getProduct(productId)
            .orElseGet(() -> {
                Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
                Brand brand = brandRepository.findById(product.getBrandId())
                    .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
                ProductDto.ProductInfo result = ProductDto.ProductInfo.of(product, brand.getName());
                productCacheStore.putProduct(productId, result);
                return result;
            });

        eventPublisher.publishEvent(new ProductViewedEvent(productId, null, Instant.now()));

        return info;
    }

    @Transactional
    public ProductDto.ProductInfo increaseStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        productService.increaseStock(product, quantity);
        Product saved = productRepository.save(product);
        Brand brand = brandRepository.findById(saved.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        productCacheStore.evictProduct(productId);
        return ProductDto.ProductInfo.of(saved, brand.getName());
    }

    @Transactional
    public ProductDto.ProductInfo decreaseStock(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        try {
            productService.decreaseStock(product, quantity);
        } catch (ProductInsufficientStockException e) {
            throw new CoreException(ErrorType.PRODUCT_INSUFFICIENT_STOCK);
        }
        Product saved = productRepository.save(product);
        Brand brand = brandRepository.findById(saved.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        productCacheStore.evictProduct(productId);
        return ProductDto.ProductInfo.of(saved, brand.getName());
    }

    @Transactional
    public void delete(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        productService.deleteProduct(product);
        productRepository.save(product);
        productCacheStore.evictProduct(productId);
    }

    @Transactional
    public ProductDto.ProductInfo restore(Long productId) {
        Product product = productRepository.findByIdIncludingDeleted(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

        try {
            productService.restoreProduct(product, productId);
        } catch (ProductNotDeletedException e) {
            throw new CoreException(ErrorType.PRODUCT_NOT_DELETED);
        }
        Product saved = productRepository.save(product);
        Brand brand = brandRepository.findByIdIncludingDeleted(saved.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        productCacheStore.evictProduct(productId);
        return ProductDto.ProductInfo.of(saved, brand.getName());
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim();
    }
}
