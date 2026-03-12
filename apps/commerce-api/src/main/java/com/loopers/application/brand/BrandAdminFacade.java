package com.loopers.application.brand;

import com.loopers.application.cache.ProductCacheManager;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.cart.CartItemService;
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
    private final CartItemService cartItemService;
    private final ProductCacheManager productCacheManager;

    public BrandAdminFacade(BrandService brandService, ProductService productService,
                            InventoryService inventoryService, CartItemService cartItemService,
                            ProductCacheManager productCacheManager) {
        this.brandService = brandService;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.cartItemService = cartItemService;
        this.productCacheManager = productCacheManager;
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

    /** 브랜드 삭제 + 소속 상품/재고/장바구니 연쇄 삭제 */
    @Transactional
    public void deleteBrand(Long brandId) {
        brandService.delete(brandId);

        List<Product> products = productService.getAllProductsByBrandId(brandId);
        List<Long> productIds = products.stream().map(Product::getId).toList();

        for (Product product : products) {
            productService.delete(product.getId());
            inventoryService.delete(product.getId());
            cartItemService.deleteByProductId(product.getId());
        }

        productCacheManager.registerBrandDeleteDoubleDelete(productIds);
    }

    /** 전체 브랜드 목록 페이지네이션 조회 */
    @Transactional(readOnly = true)
    public BrandAdminListResult getAllBrands(int page, int size) {
        List<Brand> brands = brandService.getAllBrands(page, size);
        long totalElements = brandService.countAllBrands();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<BrandInfo> brandInfos = brands.stream()
                .map(BrandInfo::from)
                .toList();

        return new BrandAdminListResult(brandInfos, page, size, totalElements, totalPages);
    }

    /** 브랜드 생성 */
    @Transactional
    public BrandInfo createBrand(String name, String description) {
        Brand brand = brandService.create(name, description);
        return BrandInfo.from(brand);
    }

    /** 브랜드 수정 */
    @Transactional
    public BrandInfo updateBrand(Long brandId, String name, String description) {
        Brand brand = brandService.update(brandId, name, description);
        return BrandInfo.from(brand);
    }

    /** 브랜드 상태 변경 — 상품 목록 캐시 무효화 (브랜드 필터 변경) */
    @Transactional
    public BrandInfo changeBrandStatus(Long brandId, BrandStatus status) {
        Brand brand = brandService.changeStatus(brandId, status);

        productCacheManager.registerListOnlyDoubleDelete();

        return BrandInfo.from(brand);
    }

    public record BrandAdminDetailResult(BrandInfo brand, List<ProductInfo> products) {}

    public record BrandAdminListResult(
            List<BrandInfo> brands,
            int page, int size, long totalElements, int totalPages) {}
}
