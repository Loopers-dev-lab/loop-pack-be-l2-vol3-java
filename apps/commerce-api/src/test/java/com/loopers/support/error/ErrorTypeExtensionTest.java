package com.loopers.support.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorType 확장 테스트")
class ErrorTypeExtensionTest {

    @Test
    @DisplayName("모든 신규 에러 타입이 올바른 HttpStatus와 code를 가진다")
    void allNewErrorTypes_ShouldHaveCorrectHttpStatusAndCode() {
        // User
        assertThat(ErrorType.USER_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorType.USER_NOT_FOUND.getCode()).isEqualTo("USER_NOT_FOUND");
        assertThat(ErrorType.DUPLICATE_USER_ID.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorType.DUPLICATE_USER_ID.getCode()).isEqualTo("DUPLICATE_USER_ID");

        // Brand
        assertThat(ErrorType.BRAND_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorType.BRAND_NOT_FOUND.getCode()).isEqualTo("BRAND_NOT_FOUND");
        assertThat(ErrorType.DUPLICATE_BRAND.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorType.DUPLICATE_BRAND.getCode()).isEqualTo("DUPLICATE_BRAND");

        // Product
        assertThat(ErrorType.PRODUCT_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorType.PRODUCT_NOT_FOUND.getCode()).isEqualTo("PRODUCT_NOT_FOUND");
        assertThat(ErrorType.PRODUCT_NOT_ORDERABLE.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorType.PRODUCT_NOT_ORDERABLE.getCode()).isEqualTo("PRODUCT_NOT_ORDERABLE");
        assertThat(ErrorType.INVALID_STOCK_UPDATE.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorType.INVALID_STOCK_UPDATE.getCode()).isEqualTo("INVALID_STOCK_UPDATE");

        // Stock
        assertThat(ErrorType.STOCK_NOT_ENOUGH.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorType.STOCK_NOT_ENOUGH.getCode()).isEqualTo("STOCK_NOT_ENOUGH");

        // Like
        assertThat(ErrorType.LIKE_PRODUCT_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorType.LIKE_PRODUCT_NOT_FOUND.getCode()).isEqualTo("LIKE_PRODUCT_NOT_FOUND");

        // Cart
        assertThat(ErrorType.CART_ITEM_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorType.CART_ITEM_NOT_FOUND.getCode()).isEqualTo("CART_ITEM_NOT_FOUND");
        assertThat(ErrorType.CART_LIMIT_EXCEEDED.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorType.CART_LIMIT_EXCEEDED.getCode()).isEqualTo("CART_LIMIT_EXCEEDED");
        assertThat(ErrorType.CART_STOCK_EXCEEDED.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorType.CART_STOCK_EXCEEDED.getCode()).isEqualTo("CART_STOCK_EXCEEDED");

        // Order
        assertThat(ErrorType.ORDER_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorType.ORDER_NOT_FOUND.getCode()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(ErrorType.ORDER_NOT_CANCELLABLE.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorType.ORDER_NOT_CANCELLABLE.getCode()).isEqualTo("ORDER_NOT_CANCELLABLE");
        assertThat(ErrorType.ORDER_NOT_CREATABLE.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorType.ORDER_NOT_CREATABLE.getCode()).isEqualTo("ORDER_NOT_CREATABLE");
        assertThat(ErrorType.ORDER_ITEM_EMPTY.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorType.ORDER_ITEM_EMPTY.getCode()).isEqualTo("ORDER_ITEM_EMPTY");
        assertThat(ErrorType.ORDER_PENDING_LIMIT_EXCEEDED.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorType.ORDER_PENDING_LIMIT_EXCEEDED.getCode()).isEqualTo("ORDER_PENDING_LIMIT_EXCEEDED");

        // Admin
        assertThat(ErrorType.ADMIN_UNAUTHORIZED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorType.ADMIN_UNAUTHORIZED.getCode()).isEqualTo("ADMIN_UNAUTHORIZED");
    }
}
