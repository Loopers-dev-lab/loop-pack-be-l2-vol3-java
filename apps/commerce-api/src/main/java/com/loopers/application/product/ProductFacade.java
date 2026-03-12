package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.common.CursorResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductCursor;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 고객 상품 조회 Facade
 *
 * Product + Brand 도메인 서비스를 조합하여 고객 상품 조회 유스케이스를 처리한다.
 */
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;

    public ProductFacade(ProductService productService, BrandService brandService) {
        this.productService = productService;
        this.brandService = brandService;
    }

    /** 고객 상품 상세 조회 (상품 + 브랜드명) */
    @Transactional(readOnly = true)
    public ProductDetailResult getProductDetail(Long productId) {
        Product product = productService.getDisplayableProduct(productId);
        Brand brand = brandService.getActiveBrand(product.getBrandId());
        return new ProductDetailResult(ProductInfo.from(product), BrandInfo.from(brand));
    }

    /** 고객 상품 목록 커서 조회 (COUNT 쿼리 없음) */
    @Transactional(readOnly = true)
    public ProductCursorResult getDisplayableProductsWithCursor(Long brandId, ProductSortType sort, ProductCursor cursor, int size) {
        CursorResult<Product> result = productService.getDisplayableProductsWithCursor(brandId, sort, cursor, size);

        List<ProductInfo> productInfos = result.items().stream()
                .map(ProductInfo::from)
                .toList();

        return new ProductCursorResult(productInfos, result.hasNext(), size);
    }

    public record ProductDetailResult(ProductInfo product, BrandInfo brand) {}

    public record ProductCursorResult(
            List<ProductInfo> products,
            boolean hasNext,
            int size
    ) {}
}
