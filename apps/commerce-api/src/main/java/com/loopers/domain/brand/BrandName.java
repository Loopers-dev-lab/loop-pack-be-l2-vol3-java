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
public class BrandName {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 50;

    @Column(name = "name", nullable = false)
    private String value;

    public BrandName(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (Objects.isNull(value)) {
            throw new CoreException(ErrorType.REQUIRED_BRAND_NAME);
        }

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.INVALID_BRAND_NAME);
        }
    }
}