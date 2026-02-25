package com.loopers.interfaces.api.cart;

import com.loopers.application.cart.CartFacade;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cart")
public class CartV1Controller implements CartV1ApiSpec {

    private final CartFacade cartFacade;
    private final UserFacade userFacade;

    public CartV1Controller(CartFacade cartFacade, UserFacade userFacade) {
        this.cartFacade = cartFacade;
        this.userFacade = userFacade;
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<CartV1Dto.CartItemResponse> addItem(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @Valid @RequestBody CartV1Dto.AddItemRequest request
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        var info = cartFacade.addItem(
            userId,
            request.productId(),
            request.optionId(),
            request.quantity() != null ? request.quantity() : 1
        );
        return ApiResponse.success(CartV1Dto.CartItemResponse.from(info));
    }

    @GetMapping("/items")
    @Override
    public ApiResponse<List<CartV1Dto.CartItemResponse>> getItems(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId
    ) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다: " + loginId));
        List<CartV1Dto.CartItemResponse> list = cartFacade.getItems(userId).stream()
            .map(CartV1Dto.CartItemResponse::from)
            .toList();
        return ApiResponse.success(list);
    }

    @PutMapping("/items/{cartItemId}")
    @Override
    public ApiResponse<CartV1Dto.CartItemResponse> updateItem(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @PathVariable Long cartItemId,
        @Valid @RequestBody CartV1Dto.UpdateItemRequest request
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        var info = cartFacade.updateItem(
            userId,
            cartItemId,
            request.quantity(),
            request.optionId()
        );
        return ApiResponse.success(CartV1Dto.CartItemResponse.from(info));
    }

    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public ApiResponse<Void> removeItems(
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @Valid @RequestBody CartV1Dto.RemoveItemsRequest request
    ) {
        Long userId = userFacade.findUserIdByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다."));
        List<Long> ids = request.cartItemIds() != null ? request.cartItemIds() : Collections.emptyList();
        cartFacade.removeItems(userId, ids);
        return ApiResponse.success(null);
    }
}
