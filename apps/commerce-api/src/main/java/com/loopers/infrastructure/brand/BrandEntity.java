package com.loopers.infrastructure.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.brand.Brand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 *  브랜드 DB 엔티티
 */
@Entity
@Table(name = "brand")
@NoArgsConstructor
public class BrandEntity extends BaseEntity {

    @Comment("브랜드 이름")
    @Column(name = "name", nullable = false)
    private String name;

    @Comment("브랜드 설명")
    @Column(name = "description", nullable = true)
    private String description;

    private BrandEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static BrandEntity create(Brand brand) {
        return new BrandEntity(
            brand.getName(),
            brand.getDescription()
        );
    }

    public static Brand toDomain(BrandEntity brandEntity) {
        return Brand.create(brandEntity.getId(), brandEntity.getName(), brandEntity.getDescription());
    }

    public void update(Brand brand) {
        this.name = brand.getName();
        this.description = brand.getDescription();
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }
}
