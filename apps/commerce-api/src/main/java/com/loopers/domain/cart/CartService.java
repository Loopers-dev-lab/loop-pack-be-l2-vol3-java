package com.loopers.domain.cart;

import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.Quantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartService {

    private static final String CART_ITEM_NOT_FOUND = "장바구니 항목을 찾을 수 없습니다.";
    private static final String CART_ITEM_NOT_FOUND_WITH_ID = "장바구니 항목을 찾을 수 없습니다: %d";

    private final CartRepository cartRepository;
    private final ProductService productService;

    public CartService(CartRepository cartRepository, ProductService productService) {
        this.cartRepository = cartRepository;
        this.productService = productService;
    }

    /**
     * 장바구니에 항목을 추가한다. 동일 상품·동일 옵션이 있으면 수량을 합산한다.
     */
    @Transactional
    public CartItemModel addItem(Long userId, Long productId, Long optionId, int quantity) {
        Quantity q = Quantity.of(quantity);
        productService.validateProductAvailability(productId, q, optionId);

        List<CartItemModel> items = cartRepository.findByUserId(userId);
        CartItemModel existing = findExistingSameProduct(items, productId, optionId);

        if (existing != null) {
            Quantity newQ = Quantity.of(existing.getQuantity() + quantity);
            productService.validateProductAvailability(productId, newQ, optionId);
            existing.updateQuantity(newQ);
            return cartRepository.save(existing);
        }

        CartItemModel newItem = CartItemModel.create(userId, productId, optionId, q);
        return cartRepository.save(newItem);
    }

    private CartItemModel findExistingSameProduct(List<CartItemModel> items, Long productId, Long optionId) {
        return items.stream()
                .filter(item -> item.isSameProduct(productId, optionId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 사용자의 장바구니 항목 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<CartItemModel> getItems(Long userId) {
        return cartRepository.findByUserId(userId);
    }

    /**
     * 장바구니 항목의 수량·옵션을 수정한다.
     */
    @Transactional
    public CartItemModel updateItem(Long userId, Long cartItemId, int quantity, Long optionId) {
        CartItemModel item = cartRepository.findByUserIdAndCartItemId(userId, cartItemId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, CART_ITEM_NOT_FOUND));

        Quantity q = Quantity.of(quantity);
        productService.validateProductAvailability(item.getProductId(), q, optionId);
        item.updateQuantityAndOption(q, optionId);
        return cartRepository.save(item);
    }

    /**
     * 장바구니 항목들을 삭제한다. 하나라도 소유하지 않거나 없으면 NOT_FOUND.
     */
    @Transactional
    public void removeItems(Long userId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return;
        }
        for (Long cartItemId : cartItemIds) {
            CartItemModel item = cartRepository.findByUserIdAndCartItemId(userId, cartItemId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                            String.format(CART_ITEM_NOT_FOUND_WITH_ID, cartItemId)));
            cartRepository.delete(item);
        }
    }
}
