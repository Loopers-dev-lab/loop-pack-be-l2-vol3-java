package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.application.brand.BrandService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockStatus;
import com.loopers.application.stock.StockService;
import com.loopers.domain.product.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final StockService stockService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProductDetail register(String name, String description, Money price, Long brandId, int initialStock) {
        brandService.getBrand(brandId);
        ProductModel product = productService.register(name, description, price, brandId);
        stockService.save(product.getId(), initialStock);
        String brandName = getBrandName(product.getBrandId());
        StockModel stock = stockService.getByProductId(product.getId());
        return ProductDetail.ofAdmin(product, brandName, stock.getQuantity());
    }

    @Cacheable(cacheNames = "productDetail", key = "#productId")
    @Transactional
    public ProductDetail getProduct(Long productId) {
        ProductModel product = productService.getById(productId);
        String brandName = getBrandName(product.getBrandId());
        StockModel stock = stockService.getByProductId(productId);
        eventPublisher.publishEvent(new ProductViewedEvent(productId, null));
        return ProductDetail.ofCustomer(product, brandName, StockStatus.from(stock.getQuantity()));
    }

    @Transactional(readOnly = true)
    public ProductDetail getProductForAdmin(Long productId) {
        ProductModel product = productService.getById(productId);
        String brandName = getBrandName(product.getBrandId());
        StockModel stock = stockService.getByProductId(productId);
        return ProductDetail.ofAdmin(product, brandName, stock.getQuantity());
    }

    @Transactional(readOnly = true)
    public Page<ProductDetail> getProducts(Long brandId, ProductSortType sortType, Pageable pageable) {
        Page<ProductModel> products = productService.getProducts(brandId, sortType, pageable);

        List<Long> brandIds = products.getContent().stream()
            .map(ProductModel::getBrandId).distinct().toList();
        List<Long> productIds = products.getContent().stream()
            .map(ProductModel::getId).toList();

        Map<Long, BrandModel> brandMap = brandService.getByIds(brandIds);
        Map<Long, StockModel> stockMap = stockService.getByProductIds(productIds);

        return products.map(product -> {
            BrandModel brand = brandMap.get(product.getBrandId());
            String brandName = brand != null ? brand.getName() : null;
            StockModel stock = stockMap.get(product.getId());
            StockStatus status = stock != null ? StockStatus.from(stock.getQuantity()) : StockStatus.OUT_OF_STOCK;
            return ProductDetail.ofCustomer(product, brandName, status);
        });
    }

    @Transactional(readOnly = true)
    public Page<ProductDetail> getProductsForAdmin(Long brandId, Pageable pageable) {
        Page<ProductModel> products = productService.getProductsForAdmin(brandId, pageable);

        List<Long> brandIds = products.getContent().stream()
            .map(ProductModel::getBrandId).distinct().toList();
        List<Long> productIds = products.getContent().stream()
            .map(ProductModel::getId).toList();

        Map<Long, BrandModel> brandMap = brandService.getByIds(brandIds);
        Map<Long, StockModel> stockMap = stockService.getByProductIds(productIds);

        return products.map(product -> {
            BrandModel brand = brandMap.get(product.getBrandId());
            String brandName = brand != null ? brand.getName() : null;
            StockModel stock = stockMap.get(product.getId());
            int stockQuantity = stock != null ? stock.getQuantity() : 0;
            return ProductDetail.ofAdmin(product, brandName, stockQuantity);
        });
    }

    @CacheEvict(cacheNames = "productDetail", key = "#productId")
    @Transactional
    public ProductDetail update(Long productId, String name, String description, Money price) {
        productService.update(productId, name, description, price);
        return getProductForAdmin(productId);
    }

    @CacheEvict(cacheNames = "productDetail", key = "#productId")
    public void delete(Long productId) {
        productService.delete(productId);
    }

    @CacheEvict(cacheNames = "productDetail", key = "#productId")
    @Transactional
    public ProductDetail updateStock(Long productId, int quantity) {
        StockModel stock = stockService.getByProductId(productId);
        stock.update(quantity);
        return getProductForAdmin(productId);
    }

    private String getBrandName(Long brandId) {
        try {
            BrandModel brand = brandService.getBrandForAdmin(brandId);
            return brand.getName();
        } catch (Exception e) {
            return null;
        }
    }
}
