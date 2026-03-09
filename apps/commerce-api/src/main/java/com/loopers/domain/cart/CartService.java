package com.loopers.domain.cart;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장바구니 도메인 서비스.
 * <p>
 * 장바구니 항목의 추가, 수량 변경, 삭제, 조회 및 주문 취소/만료 시 장바구니 복원을 담당한다.
 * 순수 장바구니 CRUD 로직만 포함하며,
 * 상품/브랜드/재고 등 외부 도메인 검증은 {@link com.loopers.application.cart.CartFacade}에서 수행한다.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    /**
     * 장바구니 복원 항목.
     * <p>주문 취소/만료 시 DIRECT 주문 항목을 장바구니로 복원하기 위한 값 객체이다.</p>
     *
     * @param productId 상품 ID
     * @param quantity  복원할 수량
     */
    public record RestoreItem(String productId, int quantity) {
    }

    private final CartItemRepository cartItemRepository;

    /**
     * 장바구니에 상품을 추가한다.
     * <p>
     * 동일 상품이 이미 존재하면 수량을 병합하고, 없으면 새로 생성한다.
     * 상품 주문 가능 여부 및 재고 검증은 Facade에서 수행한다.
     * </p>
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     * @param qty       추가할 수량
     */
    @Transactional
    public void addItem(String userId, String productId, int qty) {
        CartItemId cartItemId = new CartItemId(userId, productId);
        cartItemRepository.findById(cartItemId).ifPresentOrElse(
                existingItem -> existingItem.mergeQuantity(qty),
                () -> cartItemRepository.save(CartItemModel.create(userId, productId, qty))
        );
    }

    /**
     * 장바구니 항목의 수량을 변경한다.
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     * @param newQty    변경할 새 수량
     * @throws CoreException 장바구니 항목이 존재하지 않을 때 (CART_ITEM_NOT_FOUND)
     */
    @Transactional
    public void changeQuantity(String userId, String productId, int newQty) {
        CartItemId cartItemId = new CartItemId(userId, productId);
        CartItemModel item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CoreException(ErrorType.CART_ITEM_NOT_FOUND));
        item.changeQuantity(newQty);
    }

    /**
     * 장바구니에서 상품을 삭제한다. 항목이 존재하지 않으면 무시한다.
     *
     * @param userId    사용자 ID
     * @param productId 상품 ID
     */
    @Transactional
    public void removeItem(String userId, String productId) {
        CartItemId cartItemId = new CartItemId(userId, productId);
        cartItemRepository.findById(cartItemId).ifPresent(cartItemRepository::delete);
    }

    /**
     * 사용자의 장바구니 항목 목록을 조회한다.
     * <p>
     * 순수 장바구니 항목만 반환하며, 상품/브랜드/재고 정보 조합은 Facade에서 수행한다.
     * </p>
     *
     * @param userId 사용자 ID
     * @return 장바구니 항목 엔티티 목록
     */
    public List<CartItemModel> getCartItems(String userId) {
        return cartItemRepository.findAllByUserId(userId);
    }

    /**
     * 주문 취소/만료 시 DIRECT 주문 항목을 장바구니로 복원한다.
     * <p>
     * 동일 상품이 장바구니에 존재하면 수량을 병합하고, 없으면 새 항목을 생성한다.
     * </p>
     *
     * @param userId 사용자 ID
     * @param items  복원할 항목 목록 (상품 ID + 수량)
     */
    @Transactional
    public void restoreFromOrder(String userId, List<RestoreItem> items) {
        for (RestoreItem item : items) {
            CartItemId cartItemId = new CartItemId(userId, item.productId());
            cartItemRepository.findById(cartItemId).ifPresentOrElse(
                    existingItem -> existingItem.mergeQuantity(item.quantity()),
                    () -> cartItemRepository.save(
                            CartItemModel.create(userId, item.productId(), item.quantity()))
            );
        }
    }
}
