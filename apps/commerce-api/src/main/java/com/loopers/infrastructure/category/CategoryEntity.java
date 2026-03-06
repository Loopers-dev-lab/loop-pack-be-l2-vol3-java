package com.loopers.infrastructure.category;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class CategoryEntity extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;

    protected CategoryEntity() {}

    public CategoryEntity(String name) {
        this.name = name;
    }

    public static CategoryEntity from(Category category) {
        return new CategoryEntity(category.name());
    }

    public Category toDomain() {
        return new Category(getId(), name);
    }
}
