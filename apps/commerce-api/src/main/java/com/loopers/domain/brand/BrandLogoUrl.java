package com.loopers.domain.brand;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class BrandLogoUrl {

    @Column(name = "logo_url", nullable = false)
    private String value;

    public BrandLogoUrl(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (Objects.isNull(value)) {
            throw new CoreException(ErrorType.REQUIRED_BRAND_LOGO_URL);
        }
    }
}