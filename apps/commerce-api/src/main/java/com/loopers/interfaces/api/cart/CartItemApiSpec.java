package com.loopers.interfaces.api.cart;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Cart API", description = "장바구니 API")
public interface CartItemApiSpec {

    @Operation(summary = "장바구니 조회", description = "장바구니에 담긴 상품 목록을 조회합니다. 상품 정보가 실시간으로 반영됩니다.")
    ApiResponse<CartItemResponse.CartListResponse> getCart(@AuthUser User user);

    @Operation(summary = "장바구니 상품 추가", description = "장바구니에 상품을 추가합니다. 이미 존재하는 상품이면 수량이 합산됩니다.")
    ApiResponse<Object> addToCart(@AuthUser User user, CartItemRequest.AddCartItemRequest request);

    @Operation(summary = "장바구니 수량 변경", description = "장바구니 항목의 수량을 변경합니다.")
    ApiResponse<Object> changeQuantity(@AuthUser User user, Long cartItemId, CartItemRequest.ChangeQuantityRequest request);

    @Operation(summary = "장바구니 상품 삭제", description = "장바구니에서 상품을 삭제합니다.")
    ApiResponse<Object> deleteCartItem(@AuthUser User user, Long cartItemId);
}
