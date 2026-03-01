package com.loopers.domain.product;

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
public class ProductThumbnailUrl {

    @Column(name = "thumbnail_url", nullable = false)
    private String value;

    public ProductThumbnailUrl(String value) {
        validate(value);
        this.value = value;
    }

    private void validate(String value) {
        if (Objects.isNull(value)) {
            throw new CoreException(ErrorType.REQUIRED_PRODUCT_THUMBNAIL_URL);
        }
    }
}