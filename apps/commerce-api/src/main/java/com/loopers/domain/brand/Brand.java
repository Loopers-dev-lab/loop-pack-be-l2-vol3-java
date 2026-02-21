package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.BrandErrorType;
import com.loopers.support.error.CoreException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "brands")
public class Brand extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BrandStatus status;

    protected Brand() {}

    private Brand(String name, String description) {
        this.name = name;
        this.description = description;
        this.status = BrandStatus.ACTIVE;
    }

    public static Brand create(String name, String description) {
        return new Brand(name, description);
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void changeStatus(BrandStatus status) {
        this.status = status;
    }

    @Override
    public void delete() {
        if (getDeletedAt() != null) {
            throw new CoreException(BrandErrorType.ALREADY_DELETED);
        }
        super.delete();
    }

    public boolean isActive() {
        return this.status == BrandStatus.ACTIVE;
    }

    public void validateNotDeleted() {
        if (getDeletedAt() != null) {
            throw new CoreException(BrandErrorType.ALREADY_DELETED);
        }
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public BrandStatus getStatus() {
        return this.status;
    }
}
