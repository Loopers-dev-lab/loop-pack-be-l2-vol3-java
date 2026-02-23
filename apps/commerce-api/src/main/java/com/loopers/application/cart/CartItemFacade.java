package com.loopers.application.cart;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartItemService;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.support.error.CartItemErrorType;
import com.loopers.support.error.CoreException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 장바구니 Facade
 *
 * CartItem + Product + Brand + Inventory 도메인 서비스를 조합하여
 * 장바구니 관련 유스케이스를 처리한다.
 */
@Component
public class CartItemFacade {

    private final CartItemService cartItemService;
    private final ProductService productService;
    private final BrandService brandService;
    private final InventoryService inventoryService;

    public CartItemFacade(CartItemService cartItemService, ProductService productService,
                          BrandService brandService, InventoryService inventoryService) {
        this.cartItemService = cartItemService;
        this.productService = productService;
        this.brandService = brandService;
        this.inventoryService = inventoryService;
    }

    /** 장바구니 조회 (장바구니 항목 + 상품 + 브랜드 + 재고 조합) */
    @Transactional(readOnly = true)
    public CartListResult getCart(Long userId) {
        List<CartItem> items = cartItemService.getCartItems(userId);

        if (items.isEmpty()) {
            return new CartListResult(List.of());
        }

        List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
        Map<Long, Product> productMap = productService.getProductsByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId).distinct().toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        Map<Long, Inventory> inventoryMap = inventoryService.getByProductIds(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        List<CartItemDetail> details = items.stream()
                .filter(item -> productMap.containsKey(item.getProductId()))
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    Brand brand = brandMap.get(product.getBrandId());
                    String brandName = brand != null ? brand.getName() : "";
                    Inventory inventory = inventoryMap.get(item.getProductId());
                    int availableStock = inventory != null ? inventory.getAvailableQuantity() : 0;
                    return new CartItemDetail(
                            item.getId(), product.getId(), product.getName(),
                            brandName, product.getBasePrice(), item.getQuantity(),
                            availableStock, product.getStatus().name());
                })
                .toList();

        return new CartListResult(details);
    }

    /** 장바구니 추가 (상품 존재 + ACTIVE 검증 → CartItemService에 위임) */
    @Transactional
    public void addToCart(Long userId, Long productId, int quantity) {
        Product product = productService.getDisplayableProduct(productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new CoreException(CartItemErrorType.NOT_PURCHASABLE);
        }
        cartItemService.addToCart(userId, productId, quantity);
    }

    /** 장바구니 수량 변경 */
    @Transactional
    public void changeQuantity(Long cartItemId, Long userId, int quantity) {
        cartItemService.changeQuantity(cartItemId, userId, quantity);
    }

    /** 장바구니 삭제 */
    @Transactional
    public void deleteCartItem(Long cartItemId, Long userId) {
        cartItemService.delete(cartItemId, userId);
    }

    public record CartItemDetail(
            Long cartItemId, Long productId, String productName,
            String brandName, int basePrice, int quantity,
            int availableStock, String productStatus) {}

    public record CartListResult(List<CartItemDetail> items) {}
}
