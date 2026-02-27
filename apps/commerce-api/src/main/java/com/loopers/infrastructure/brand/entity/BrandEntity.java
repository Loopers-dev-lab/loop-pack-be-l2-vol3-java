package com.loopers.infrastructure.brand.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.model.Brand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "brand")
@SQLRestriction("deleted_at IS NULL")
public class BrandEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;
    @Column
    private String description;

    protected BrandEntity() {}

    private BrandEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static BrandEntity toEntity(Brand brand) {
        return new BrandEntity(
                brand.getName().value(),
                brand.getDescription()
        );
    }

    public Brand toModel() {
        return Brand.reconstruct(
                this.getId(),
                this.name,
                this.description
        );
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
