package com.loopers.domain.brand;

import com.loopers.domain.brand.vo.BrandName;

public record Brand(
        Long id,
        BrandName name,
        String description,
        String imageUrl
) {

    public Brand(BrandName name, String description, String imageUrl) {
        this(null, name, description, imageUrl);
    }

    public Brand updateDescription(String description) {
        return new Brand(this.id, this.name, description, this.imageUrl);
    }

    public Brand updateImageUrl(String imageUrl) {
        return new Brand(this.id, this.name, this.description, imageUrl);
    }

    public Brand update(String description, String imageUrl) {
        return new Brand(this.id, this.name, description, imageUrl);
    }

    public boolean canDelete() {
        return true;
    }
}
