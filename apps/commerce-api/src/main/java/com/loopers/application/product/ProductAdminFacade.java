package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.inventory.InventoryInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.support.error.BrandErrorType;
import com.loopers.support.error.CoreException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 어드민 상품 Facade
 *
 * Product + Brand + Inventory 도메인 서비스를 조합하여 어드민 상품 유스케이스를 처리한다.
 */
@Component
public class ProductAdminFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final InventoryService inventoryService;

    public ProductAdminFacade(ProductService productService, BrandService brandService,
                              InventoryService inventoryService) {
        this.productService = productService;
        this.brandService = brandService;
        this.inventoryService = inventoryService;
    }

    /** 상품 등록 (브랜드 ACTIVE 검증 + 상품 생성 + 재고 생성) */
    @Transactional
    public ProductAdminDetailResult createProduct(Long brandId, String name, String description,
                                                   int basePrice, int quantity) {
        Brand brand = brandService.getById(brandId);
        if (!brand.isActive()) {
            throw new CoreException(BrandErrorType.INACTIVE_BRAND);
        }

        Product product = productService.create(brandId, name, description, basePrice);
        Inventory inventory = inventoryService.create(product.getId(), quantity);

        return new ProductAdminDetailResult(
                ProductInfo.from(product), BrandInfo.from(brand), InventoryInfo.from(inventory));
    }

    /** 어드민 상품 상세 조회 (상품 + 브랜드 + 재고) */
    @Transactional(readOnly = true)
    public ProductAdminDetailResult getProductDetail(Long productId) {
        Product product = productService.getById(productId);
        Brand brand = brandService.getById(product.getBrandId());
        Inventory inventory = inventoryService.getByProductId(productId);

        return new ProductAdminDetailResult(
                ProductInfo.from(product), BrandInfo.from(brand), InventoryInfo.from(inventory));
    }

    /** 어드민 상품 목록 조회 (상품 목록 + 재고 수량) */
    @Transactional(readOnly = true)
    public ProductAdminListResult getProducts(int page, int size, Long brandId) {
        List<Product> products = productService.getAllProducts(page, size, brandId);
        long totalElements = productService.countAllProducts(brandId);
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<ProductInfo> productInfos = products.stream()
                .map(ProductInfo::from)
                .toList();

        return new ProductAdminListResult(productInfos, page, size, totalElements, totalPages);
    }

    /** 상품 삭제 (상품 + 재고 연쇄 soft delete) */
    @Transactional
    public void deleteProduct(Long productId) {
        productService.delete(productId);
        inventoryService.delete(productId);
    }

    /** 상품 부분 수정 */
    @Transactional
    public ProductAdminDetailResult updateProduct(Long productId, String name, String description, Integer basePrice) {
        productService.update(productId, name, description, basePrice);
        return getProductDetail(productId);
    }

    /** 상품 상태 변경 */
    @Transactional
    public ProductAdminDetailResult changeProductStatus(Long productId, ProductStatus status) {
        productService.changeStatus(productId, status);
        return getProductDetail(productId);
    }

    public record ProductAdminDetailResult(ProductInfo product, BrandInfo brand, InventoryInfo inventory) {}

    public record ProductAdminListResult(
            List<ProductInfo> products,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
