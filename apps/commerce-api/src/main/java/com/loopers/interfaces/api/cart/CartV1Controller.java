package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartFacade;
import com.loopers.application.cart.CartInfo;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 장바구니 고객 API V1 REST 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>장바구니 조회, 상품 추가, 수량 변경, 상품 삭제 기능을 제공한다.
 * 복잡한 도메인으로 {@link CartFacade}를 통해 여러 서비스를 조합하여 처리한다.
 * 모든 처리는 {@link CartFacade}를 통해 위임한다.</p>
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartV1Controller {

    private final CartFacade cartFacade;

    /**
     * 장바구니 항목 목록을 조회한다.
     *
     * @param user 인증된 사용자 (인터셉터에서 주입)
     * @return 장바구니 항목 목록 응답 (주문 가능 여부 포함)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CartV1Dto.CartItemResponse>>> getCart(@AuthUser UserModel user) {
        List<CartInfo> cart = cartFacade.getCart(user.getUserId());
        List<CartV1Dto.CartItemResponse> response = cart.stream()
                .map(CartV1Dto.CartItemResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 장바구니에 상품을 추가한다.
     *
     * @param user 인증된 사용자 (인터셉터에서 주입)
     * @param request 상품 추가 요청 (상품 ID, 수량)
     * @return 성공 응답
     */
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<Object>> addItem(
            @AuthUser UserModel user,
            @Valid @RequestBody CartV1Dto.AddItemRequest request) {
        cartFacade.addItem(user.getUserId(), request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 장바구니 항목의 수량을 변경한다.
     *
     * @param user 인증된 사용자 (인터셉터에서 주입)
     * @param productId 수량을 변경할 상품 ID
     * @param request 수량 변경 요청 (새 수량)
     * @return 성공 응답
     */
    @PatchMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<Object>> changeQuantity(
            @AuthUser UserModel user,
            @PathVariable Long productId,
            @Valid @RequestBody CartV1Dto.ChangeQtyRequest request) {
        cartFacade.changeQuantity(user.getUserId(), productId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 장바구니에서 상품을 삭제한다.
     *
     * @param user 인증된 사용자 (인터셉터에서 주입)
     * @param productId 삭제할 상품 ID
     * @return 성공 응답
     */
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<Object>> removeItem(
            @AuthUser UserModel user,
            @PathVariable Long productId) {
        cartFacade.removeItem(user.getUserId(), productId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
