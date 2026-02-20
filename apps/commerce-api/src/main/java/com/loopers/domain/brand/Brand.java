package com.loopers.domain.brand;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brand")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Brand extends BaseEntity {

    private String name;

    private String logoUrl;

    private String description;

    public static Brand create(String name, String logoUrl, String description) {
        if  (Objects.isNull(name)) {
            throw new CoreException(ErrorType.REQUIRED_BRAND_NAME);
        }

        if (name.length() < 2 || name.length() > 50) {
            throw new CoreException(ErrorType.INVALID_BRAND_NAME);
        }

        if (Objects.isNull(logoUrl)) {
            throw new CoreException(ErrorType.REQUIRED_BRAND_LOGO_URL);
        }

        Brand brand = new Brand();
        brand.name = name;
        brand.logoUrl = logoUrl;
        brand.description = description;
        return brand;
    }
}
