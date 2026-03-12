package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final StockService stockService;

    @Transactional
    public ProductModel register(String name, String description, Money price, Long brandId, int initialStock) {
        brandService.getBrand(brandId);
        ProductModel product = productService.register(name, description, price, brandId);
        stockService.create(product.getId(), initialStock);
        return product;
    }

    @Transactional(readOnly = true)
    public ProductDetail getProduct(Long productId) {
        ProductModel product = productService.getProduct(productId);
        String brandName = getBrandName(product.brandId());
        StockModel stock = stockService.getByProductId(productId);
        return ProductDetail.ofCustomer(product, brandName, stock.toStatus());
    }

    @Transactional(readOnly = true)
    public ProductDetail getProductForAdmin(Long productId) {
        ProductModel product = productService.getProductForAdmin(productId);
        String brandName = getBrandName(product.brandId());
        StockModel stock = stockService.getByProductId(productId);
        return ProductDetail.ofAdmin(product, brandName, stock.quantity());
    }

    @Transactional(readOnly = true)
    public Page<ProductDetail> getProducts(Long brandId, ProductSortType sortType, Pageable pageable) {
        Page<ProductModel> products = productService.getProducts(brandId, sortType, pageable);
        return products.map(product -> {
            String brandName = getBrandName(product.brandId());
            StockModel stock = stockService.getByProductId(product.getId());
            return ProductDetail.ofCustomer(product, brandName, stock.toStatus());
        });
    }

    @Transactional(readOnly = true)
    public Page<ProductDetail> getProductsForAdmin(Long brandId, Pageable pageable) {
        Page<ProductModel> products = productService.getProductsForAdmin(brandId, pageable);
        return products.map(product -> {
            String brandName = getBrandName(product.brandId());
            StockModel stock = stockService.getByProductId(product.getId());
            return ProductDetail.ofAdmin(product, brandName, stock.quantity());
        });
    }

    private String getBrandName(Long brandId) {
        try {
            BrandModel brand = brandService.getBrandForAdmin(brandId);
            return brand.name().value();
        } catch (Exception e) {
            return null;
        }
    }
}
