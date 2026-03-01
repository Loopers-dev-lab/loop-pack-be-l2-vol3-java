package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(
    name = "brands",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_brand_name_deleted", columnNames = {"name", "deleted_at"})
    }
)
@Getter
public class Brand extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    protected Brand() {
    }

    private Brand(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static Brand create(BrandName name, BrandDescription description) {
        if (name == null || description == null) {
            throw new IllegalArgumentException("브랜드 정보는 필수입니다");
        }

        return new Brand(name.value(), description.value());
    }

    public void update(BrandName name, BrandDescription description) {
        if (name == null || description == null) {
            throw new IllegalArgumentException("브랜드 정보는 필수입니다");
        }

        this.name = name.value();
        this.description = description.value();
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    @Override
    protected void guard() {
        new BrandName(this.name);
        new BrandDescription(this.description);
    }
}
