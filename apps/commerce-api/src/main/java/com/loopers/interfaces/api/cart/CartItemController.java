package com.loopers.interfaces.api.cart;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartItemService;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/carts")
public class CartItemController implements CartItemApiSpec {

    private final CartItemService cartItemService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final BrandService brandService;

    public CartItemController(CartItemService cartItemService, ProductService productService,
                              InventoryService inventoryService, BrandService brandService) {
        this.cartItemService = cartItemService;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.brandService = brandService;
    }

    @GetMapping
    @Override
    public ApiResponse<CartItemResponse.CartListResponse> getCart(@AuthUser User user) {
        List<CartItem> items = cartItemService.getCartItems(user.getId());

        if (items.isEmpty()) {
            return ApiResponse.success(new CartItemResponse.CartListResponse(List.of()));
        }

        List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
        Map<Long, Product> productMap = productService.getProductsByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();
        Map<Long, Brand> brandMap = brandService.getBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        Map<Long, Inventory> inventoryMap = inventoryService.getByProductIds(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, Function.identity()));

        List<CartItemResponse.CartItemSummary> summaries = items.stream()
                .filter(item -> productMap.containsKey(item.getProductId()))
                .map(item -> {
                    Product product = productMap.get(item.getProductId());
                    Brand brand = brandMap.get(product.getBrandId());
                    String brandName = brand != null ? brand.getName() : "";
                    Inventory inventory = inventoryMap.get(item.getProductId());
                    int availableStock = inventory != null ? inventory.getAvailableQuantity() : 0;
                    return new CartItemResponse.CartItemSummary(
                            item.getId(),
                            product.getId(),
                            product.getName(),
                            brandName,
                            product.getBasePrice(),
                            item.getQuantity(),
                            availableStock,
                            product.getStatus().name()
                    );
                })
                .toList();

        return ApiResponse.success(new CartItemResponse.CartListResponse(summaries));
    }

    @PostMapping("/items")
    @Override
    public ApiResponse<Object> addToCart(@AuthUser User user,
                                         @RequestBody CartItemRequest.AddCartItemRequest request) {
        cartItemService.addToCart(user.getId(), request.productId(), request.quantity());
        return ApiResponse.success();
    }

    @PutMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Object> changeQuantity(@AuthUser User user,
                                               @PathVariable Long cartItemId,
                                               @RequestBody CartItemRequest.ChangeQuantityRequest request) {
        cartItemService.changeQuantity(cartItemId, user.getId(), request.quantity());
        return ApiResponse.success();
    }

    @DeleteMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<Object> deleteCartItem(@AuthUser User user,
                                               @PathVariable Long cartItemId) {
        cartItemService.delete(cartItemId, user.getId());
        return ApiResponse.success();
    }
}
