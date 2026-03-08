package com.loopers.application.cart;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.cart.Cart;
import com.loopers.domain.cart.CartDomainService;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class CartApplicationService {

    private final CartDomainService cartService;
    private final ProductDomainService productService;
    private final BrandDomainService brandService;
    private final PlatformTransactionManager transactionManager;

    private static final int MAX_RETRY = 3;

    public void addToCart(Long userId, Long productId, int quantity) {
        productService.getById(productId);
        retryOnConflict(() -> executeInNewTransaction(status ->
            cartService.addToCart(userId, productId, quantity)));
    }

    @Transactional(readOnly = true)
    public Cart getMyCart(Long userId) {
        return cartService.getCart(userId);
    }

    @Transactional(readOnly = true)
    public List<CartItemDetail> getMyCartWithDetails(Long userId) {
        Cart cart = cartService.getCart(userId);
        List<CartItem> cartItems = cart.getItems();

        Set<Long> productIds = cartItems.stream()
            .map(CartItem::getProductId)
            .collect(Collectors.toSet());
        Map<Long, Product> productMap = productService.getByIds(productIds);

        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        return cartItems.stream()
            .filter(cartItem -> productMap.containsKey(cartItem.getProductId()))
            .map(cartItem -> {
                Product product = productMap.get(cartItem.getProductId());
                Brand brand = brandMap.get(product.getBrandId());
                return brand != null ? new CartItemDetail(cartItem, product, brand) : null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    public void updateQuantity(Long cartItemId, Long userId, int quantity) {
        retryOnConflict(() -> executeInNewTransaction(status ->
            cartService.updateItemQuantity(userId, cartItemId, quantity)));
    }

    public void removeItem(Long cartItemId, Long userId) {
        retryOnConflict(() -> executeInNewTransaction(status ->
            cartService.removeItem(userId, cartItemId)));
    }

    private void executeInNewTransaction(java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(action);
    }

    private void retryOnConflict(Runnable operation) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                operation.run();
                return;
            } catch (OptimisticLockingFailureException e) {
                if (i == MAX_RETRY - 1) throw e;
            }
        }
    }
}
