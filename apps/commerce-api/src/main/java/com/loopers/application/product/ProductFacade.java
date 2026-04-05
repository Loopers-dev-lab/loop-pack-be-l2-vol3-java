package com.loopers.application.product;

import com.loopers.application.brand.BrandApp;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.ranking.ProductRankingInfo;
import com.loopers.application.ranking.RankingApp;
import com.loopers.domain.common.cursor.CursorPageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductApp productApp;
    private final BrandApp brandApp;
    private final RankingApp rankingApp;

    public ProductInfo createProduct(String productId, String brandId, String productName, BigDecimal price, int stockQuantity) {
        ProductInfo product = productApp.createProduct(productId, brandId, productName, price, stockQuantity);
        return enrichProductInfo(product);
    }

    public ProductInfo getProduct(String productId, Long memberId) {
        ProductInfo product = productApp.getProduct(productId, memberId);
        ProductInfo enriched = enrichProductInfo(product);
        return enrichWithRanking(enriched);
    }

    private ProductInfo enrichWithRanking(ProductInfo product) {
        try {
            Optional<ProductRankingInfo> ranking = rankingApp.getProductRanking(product.id(), LocalDate.now());
            if (ranking.isEmpty()) {
                return product;
            }
            return product.withRanking(ranking.get().rank(), ranking.get().score());
        } catch (Exception e) {
            return product;
        }
    }

    public ProductInfo updateProduct(String productId, String productName, BigDecimal price, int stockQuantity) {
        ProductInfo product = productApp.updateProduct(productId, productName, price, stockQuantity);
        return enrichProductInfo(product);
    }

    public void deleteProduct(String productId) {
        productApp.deleteProduct(productId);
    }

    public Page<ProductInfo> getProducts(String brandId, String sortBy, Pageable pageable) {
        Page<ProductInfo> products = productApp.getProducts(brandId, sortBy, pageable);
        return products.map(this::enrichProductInfo);
    }

    public CursorPageResult<ProductInfo> getProductsByCursor(String brandId, String sortBy, String cursor, int size) {
        CursorPageResult<ProductInfo> result = productApp.getProductsByCursor(brandId, sortBy, cursor, size);
        return result.map(this::enrichProductInfo);
    }

    public ProductInfo getProductByRefId(Long id) {
        ProductInfo product = productApp.getProductByRefId(id);
        return enrichProductInfo(product);
    }

    private ProductInfo enrichProductInfo(ProductInfo product) {
        BrandInfo brand = brandApp.getBrandByRefId(product.refBrandId());
        long likesCount = productApp.getLikesCount(product.id());
        return product.enrich(brand, likesCount);
    }
}
