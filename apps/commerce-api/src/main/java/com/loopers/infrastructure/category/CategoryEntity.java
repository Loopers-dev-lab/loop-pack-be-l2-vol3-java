package com.loopers.infrastructure.category;

import com.loopers.domain.AutoIncrementBaseEntity;
import com.loopers.domain.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity extends AutoIncrementBaseEntity {
    @Column(name = "reference_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false, unique = true)
    private UUID referenceId;

    @Column(nullable = false, unique = true)
    private String name;

    protected CategoryEntity() {}

    public CategoryEntity(UUID referenceId, String name) {
        this.referenceId = referenceId;
        this.name = name;
    }

    public static CategoryEntity from(Category category) {
        UUID resolvedReferenceId = category.id() != null ? category.id() : UUID.randomUUID();
        return new CategoryEntity(resolvedReferenceId, category.name());
    }

    public Category toDomain() {
        return new Category(referenceId, name);
    }
}
