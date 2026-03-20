package com.loopers.infrastructure.brand;

import com.loopers.domain.AutoIncrementBaseEntity;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.vo.BrandName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "brands")
public class BrandEntity extends AutoIncrementBaseEntity {
    @Column(name = "reference_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false, unique = true)
    private UUID referenceId;

    @Getter
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    protected BrandEntity() {}

    public BrandEntity(UUID referenceId, String name, String description, String imageUrl) {
        this.referenceId = referenceId;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public static BrandEntity from(Brand brand) {
        UUID resolvedReferenceId = brand.id() != null ? brand.id() : UUID.randomUUID();
        return new BrandEntity(
                resolvedReferenceId,
                brand.name().value(),
                brand.description(),
                brand.imageUrl()
        );
    }

    public Brand toDomain() {
        return new Brand(
                referenceId,
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
