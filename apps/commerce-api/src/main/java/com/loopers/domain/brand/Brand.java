package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "brands")
public class Brand extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private BrandName name;

    protected Brand() {}

    public Brand(String name) {
        this.name = new BrandName(name);
    }

    public BrandName getName() {
        return name;
    }
}
