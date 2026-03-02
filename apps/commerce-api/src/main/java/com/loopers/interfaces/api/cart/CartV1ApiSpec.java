package com.loopers.interfaces.api.cart;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;

@Tag(name = "Cart V1 API", description = "장바구니 API")
public interface CartV1ApiSpec {

    @Operation(
        summary = "장바구니 담기",
        description = "상품·옵션·수량을 장바구니에 추가합니다. 동일 상품·옵션이 있으면 수량이 합산됩니다. 로그인 필요."
    )
    ApiResponse<CartV1Dto.CartItemResponse> addItem(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Schema(description = "장바구니 추가 요청")
        @Valid CartV1Dto.AddItemRequest request
    );

    @Operation(
        summary = "장바구니 목록 조회",
        description = "로그인한 사용자의 장바구니 항목 목록을 조회합니다."
    )
    ApiResponse<List<CartV1Dto.CartItemResponse>> getItems(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId
    );

    @Operation(
        summary = "장바구니 항목 수정",
        description = "장바구니 항목의 수량·옵션을 수정합니다."
    )
    ApiResponse<CartV1Dto.CartItemResponse> updateItem(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Parameter(description = "장바구니 항목 ID", required = true)
        Long cartItemId,
        @Schema(description = "수정 요청 (수량, 옵션)")
        @Valid CartV1Dto.UpdateItemRequest request
    );

    @Operation(
        summary = "장바구니 항목 삭제",
        description = "장바구니 항목을 선택 삭제합니다."
    )
    ApiResponse<Void> removeItems(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Schema(description = "삭제할 장바구니 항목 ID 목록")
        @Valid CartV1Dto.RemoveItemsRequest request
    );
}
