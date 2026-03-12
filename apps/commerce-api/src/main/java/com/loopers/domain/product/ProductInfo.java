package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public record ProductInfo(
    Long brandId,
    String name,
    String description,
    Long price,
    Integer stock,
    String imageUrl
) {
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_IMAGE_URL_LENGTH = 500;
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");

    public ProductInfo {
        validateBrandId(brandId);
        validateName(name);
        validateDescription(description);
        validatePrice(price);
        validateStock(stock);
        validateImageUrl(imageUrl);
    }

    private void validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                String.format("상품명은 %d자를 초과할 수 없습니다.", MAX_NAME_LENGTH));
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                String.format("상품 설명은 %d자를 초과할 수 없습니다.", MAX_DESCRIPTION_LENGTH));
        }
    }

    private void validatePrice(Long price) {
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
        if (price < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0 이상이어야 합니다.");
        }
    }

    private void validateStock(Integer stock) {
        if (stock == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 필수입니다.");
        }
        if (stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
    }

    private void validateImageUrl(String imageUrl) {
        if (imageUrl == null) {
            return;
        }
        if (!URL_PATTERN.matcher(imageUrl).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미지 URL은 http 또는 https로 시작해야 합니다.");
        }
        if (imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                String.format("이미지 URL은 %d자를 초과할 수 없습니다.", MAX_IMAGE_URL_LENGTH));
        }
    }
}
