package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.cache.ProductCacheManager;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.common.CursorResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCursor;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

/**
 * 고객 상품 조회 Facade
 *
 * Product + Brand 도메인 서비스를 조합하여 고객 상품 조회 유스케이스를 처리한다.
 */
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final ProductCacheManager productCacheManager;

    public ProductFacade(ProductService productService, BrandService brandService,
                         ProductCacheManager productCacheManager) {
        this.productService = productService;
        this.brandService = brandService;
        this.productCacheManager = productCacheManager;
    }

    /** 고객 상품 상세 조회 (상품 + 브랜드명) — Cache-Aside */
    public ProductDetailResult getProductDetail(Long productId) {
        Optional<ProductDetailResult> cached = productCacheManager.getProductDetail(productId);
        if (cached.isPresent()) {
            return cached.get();
        }

        Product product = productService.getDisplayableProduct(productId);
        Brand brand = brandService.getActiveBrand(product.getBrandId());
        ProductDetailResult result = new ProductDetailResult(ProductInfo.from(product), BrandInfo.from(brand));

        productCacheManager.putProductDetail(productId, result);
        return result;
    }

    /** 고객 상품 목록 커서 조회 (COUNT 쿼리 없음) — 첫 페이지만 Cache-Aside */
    public ProductCursorResult getDisplayableProductsWithCursor(Long brandId, ProductSortType sort, ProductCursor cursor, int size) {
        boolean isFirstPage = (cursor == null);

        if (isFirstPage) {
            Optional<ProductCursorResult> cached = productCacheManager.getProductList(sort, brandId);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        CursorResult<Product> result = productService.getDisplayableProductsWithCursor(brandId, sort, cursor, size);

        List<ProductInfo> productInfos = result.items().stream()
                .map(ProductInfo::from)
                .toList();

        ProductCursorResult cursorResult = new ProductCursorResult(productInfos, result.hasNext(), size);

        if (isFirstPage) {
            productCacheManager.putProductList(sort, brandId, cursorResult);
        }

        return cursorResult;
    }

    public record ProductDetailResult(ProductInfo product, BrandInfo brand) {}

    public record ProductCursorResult(
            List<ProductInfo> products,
            boolean hasNext,
            int size
    ) {}
}
