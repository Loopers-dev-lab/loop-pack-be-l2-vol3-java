package com.loopers.interfaces.apiadmin;

import com.loopers.application.cart.CartFacade;
import com.loopers.application.cart.CartInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 전용 장바구니 REST API 엔드포인트를 제공하는 컨트롤러.
 *
 * <p>특정 사용자의 장바구니 내역을 관리자가 조회할 수 있는 기능을 제공한다.
 * {@link CartFacade}를 통해 장바구니 항목 + 상품/브랜드/재고 정보를 조합하여 반환한다.</p>
 */
@RestController
@RequestMapping("/api-admin/v1")
@RequiredArgsConstructor
public class AdminCartV1Controller {

    private final CartFacade cartFacade;

    /**
     * 특정 사용자의 장바구니 목록을 조회한다.
     *
     * @param userId 조회할 사용자 ID
     * @return 해당 사용자의 장바구니 항목 목록 응답
     */
    @GetMapping("/users/{userId}/cart")
    public ResponseEntity<ApiResponse<List<AdminCartV1Dto.AdminCartItemResponse>>> getUserCart(
            @PathVariable String userId) {
        List<CartInfo> cart = cartFacade.getCartForAdmin(userId);
        List<AdminCartV1Dto.AdminCartItemResponse> response = cart.stream()
                .map(AdminCartV1Dto.AdminCartItemResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
