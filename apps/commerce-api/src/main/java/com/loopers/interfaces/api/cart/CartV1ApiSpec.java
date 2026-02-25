package com.loopers.interfaces.api.cart;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Cart V1 API", description = "장바구니 API 입니다.")
public interface CartV1ApiSpec {

    @Operation(summary = "장바구니 담기", description = "상품을 장바구니에 담습니다. 이미 담긴 상품이면 수량이 합산됩니다.")
    ApiResponse<Void> addToCart(@Parameter(hidden = true) AuthenticatedUser authUser, CartV1Dto.AddRequest request);

    @Operation(summary = "장바구니 조회", description = "내 장바구니를 조회합니다.")
    ApiResponse<CartV1Dto.CartResponse> getMyCart(@Parameter(hidden = true) AuthenticatedUser authUser);

    @Operation(summary = "수량 변경", description = "장바구니 항목의 수량을 변경합니다.")
    ApiResponse<Void> updateQuantity(@Parameter(hidden = true) AuthenticatedUser authUser, Long cartItemId, CartV1Dto.UpdateQuantityRequest request);

    @Operation(summary = "항목 삭제", description = "장바구니에서 항목을 삭제합니다.")
    ApiResponse<Void> removeItem(@Parameter(hidden = true) AuthenticatedUser authUser, Long cartItemId);
}
