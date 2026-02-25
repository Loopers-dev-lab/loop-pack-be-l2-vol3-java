package com.loopers.infrastructure.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.vo.BrandName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "brands")
public class BrandEntity extends BaseEntity {

    @Getter
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    protected BrandEntity() {}

    public BrandEntity(String name, String description, String imageUrl) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public static BrandEntity from(Brand brand) {
        return new BrandEntity(
                brand.name().value(),
                brand.description(),
                brand.imageUrl()
        );
    }

    public Brand toDomain() {
        return new Brand(
                getId(),
                new BrandName(name),
                description,
                imageUrl
        );
    }

    public void updateFrom(Brand brand) {
        this.description = brand.description();
        this.imageUrl = brand.imageUrl();
    }
}
