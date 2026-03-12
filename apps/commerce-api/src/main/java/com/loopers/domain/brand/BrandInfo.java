package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public record BrandInfo(
    String name,
    String description,
    String logoUrl
) {
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_LOGO_URL_LENGTH = 500;
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");

    public BrandInfo {
        validateName(name);
        validateDescription(description);
        validateLogoUrl(logoUrl);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 필수입니다.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                String.format("브랜드명은 %d자를 초과할 수 없습니다.", MAX_NAME_LENGTH));
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                String.format("브랜드 설명은 %d자를 초과할 수 없습니다.", MAX_DESCRIPTION_LENGTH));
        }
    }

    private void validateLogoUrl(String logoUrl) {
        if (logoUrl == null) {
            return;
        }
        if (!URL_PATTERN.matcher(logoUrl).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로고 URL은 http 또는 https로 시작해야 합니다.");
        }
        if (logoUrl.length() > MAX_LOGO_URL_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                String.format("로고 URL은 %d자를 초과할 수 없습니다.", MAX_LOGO_URL_LENGTH));
        }
    }
}
