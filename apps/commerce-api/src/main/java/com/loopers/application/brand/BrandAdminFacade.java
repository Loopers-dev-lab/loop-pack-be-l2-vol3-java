package com.loopers.application.brand;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 어드민 브랜드 상세 + 상품 목록 + 연쇄삭제 Facade
 *
 * Brand + Product + Inventory 도메인 서비스를 조합하여
 * 어드민 브랜드 상세 조회 및 연쇄 삭제 유스케이스를 처리한다.
 */
@Component
public class BrandAdminFacade {

    private final BrandService brandService;
    private final ProductService productService;
    private final InventoryService inventoryService;

    public BrandAdminFacade(BrandService brandService, ProductService productService,
                            InventoryService inventoryService) {
        this.brandService = brandService;
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    /** 어드민 브랜드 상세 조회 (브랜드 + 전체 상품 목록, status 포함) */
    @Transactional(readOnly = true)
    public BrandAdminDetailResult getBrandDetail(Long brandId) {
        Brand brand = brandService.getById(brandId);
        List<Product> products = productService.getAllProductsByBrandId(brandId);

        List<ProductInfo> productInfos = products.stream()
                .map(ProductInfo::from)
                .toList();

        return new BrandAdminDetailResult(BrandInfo.from(brand), productInfos);
    }

    /** 브랜드 삭제 + 소속 상품/재고 연쇄 삭제 */
    @Transactional
    public void deleteBrand(Long brandId) {
        brandService.delete(brandId);

        List<Product> products = productService.getAllProductsByBrandId(brandId);
        for (Product product : products) {
            product.delete();
            inventoryService.delete(product.getId());
        }
    }

    public record BrandAdminDetailResult(BrandInfo brand, List<ProductInfo> products) {}
}
