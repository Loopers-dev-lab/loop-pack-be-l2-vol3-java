package com.loopers.domain.brand;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brand")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends BaseEntity {

    @Embedded
    private BrandName name;

    @Embedded
    private BrandLogoUrl logoUrl;

    private String description;

    public static Brand create(NewBrand newBrand) {
        Brand brand = new Brand();
        brand.name = new BrandName(newBrand.name());
        brand.logoUrl = new BrandLogoUrl(newBrand.logoUrl());
        brand.description = newBrand.description();
        return brand;
    }

    public void update(ModifyBrand brand) {
        if (isDeleted()) {
            throw new CoreException(ErrorType.ALREADY_DELETED_BRAND);
        }
        this.name = new BrandName(brand.name());
        this.logoUrl = new BrandLogoUrl(brand.logoUrl());
        this.description = brand.description();
    }

    public String getName() {
        return name.getValue();
    }

    public String getLogoUrl() {
        return logoUrl.getValue();
    }

    public String getDescription() {
        return description;
    }
}
