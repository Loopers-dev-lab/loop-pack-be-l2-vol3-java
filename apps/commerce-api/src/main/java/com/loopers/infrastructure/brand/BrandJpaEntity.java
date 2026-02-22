package com.loopers.infrastructure.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.Brand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandJpaEntity extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    private BrandJpaEntity(String name) {
        this.name = name;
    }

    public static BrandJpaEntity from(Brand brand) {
        return new BrandJpaEntity(brand.getName());
    }

    public Brand toDomain() {
        return Brand.of(getId(), name);
    }
}
