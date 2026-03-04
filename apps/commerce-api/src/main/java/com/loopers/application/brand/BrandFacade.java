package com.loopers.application.brand;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 고객 브랜드 상세 + 상품 목록 Facade
 *
 * Brand + Product 도메인 서비스를 조합하여 브랜드 상세 조회 유스케이스를 처리한다.
 */
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public BrandFacade(BrandService brandService, ProductService productService) {
        this.brandService = brandService;
        this.productService = productService;
    }

    /** 고객 브랜드 상세 조회 (브랜드 + ACTIVE 상품 목록) */
    public BrandDetailResult getBrandDetail(Long brandId) {
        Brand brand = brandService.getActiveBrand(brandId);
        List<Product> products = productService.getActiveProductsByBrandId(brandId);

        List<ProductInfo> productInfos = products.stream()
                .map(ProductInfo::from)
                .toList();

        return new BrandDetailResult(BrandInfo.from(brand), productInfos);
    }

    /** 활성 브랜드 목록 조회 */
    public List<BrandInfo> getAllActiveBrands() {
        return brandService.getAllActiveBrands().stream()
                .map(BrandInfo::from)
                .toList();
    }

    public record BrandDetailResult(BrandInfo brand, List<ProductInfo> products) {}
}
