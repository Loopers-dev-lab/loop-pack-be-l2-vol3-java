package com.loopers.domain.product.query;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record ProductCursor(
        ProductSortOption sortOption,
        String primaryValue,
        long id
) {
    public static ProductCursor from(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 3);
            String primaryValue = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return new ProductCursor(ProductSortOption.valueOf(parts[0]), primaryValue, Long.parseLong(parts[2]));
        } catch (RuntimeException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 커서입니다.");
        }
    }

    public String encode() {
        String encodedPrimary = Base64.getUrlEncoder().withoutPadding().encodeToString(primaryValue.getBytes(StandardCharsets.UTF_8));
        String raw = sortOption.name() + ":" + encodedPrimary + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
