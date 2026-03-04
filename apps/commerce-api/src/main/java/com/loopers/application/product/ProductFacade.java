package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortType;
import org.springframework.stereotype.Component;

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
    public ProductDetailResult getProductDetail(Long productId) {
        Product product = productService.getDisplayableProduct(productId);
        Brand brand = brandService.getActiveBrand(product.getBrandId());
        return new ProductDetailResult(ProductInfo.from(product), BrandInfo.from(brand));
    }

    /** 고객 상품 목록 조회 (상품 목록 + 브랜드명 조합) */
    public ProductListResult getDisplayableProducts(Long brandId, ProductSortType sort, int page, int size) {
        List<Product> products = productService.getDisplayableProducts(brandId, sort, page, size);
        long totalElements = productService.countDisplayableProducts(brandId);
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<ProductInfo> productInfos = products.stream()
                .map(ProductInfo::from)
                .toList();

        return new ProductListResult(productInfos, page, size, totalElements, totalPages);
    }

    public record ProductDetailResult(ProductInfo product, BrandInfo brand) {}

    public record ProductListResult(
            List<ProductInfo> products,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
