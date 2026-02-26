package com.loopers.application.brand;

import java.util.List;

public interface BrandQueryUseCase {

    BrandInfo getBrand(Long brandId);

    List<BrandInfo> getBrands();

    record BrandInfo(
            Long id,
            String name,
            String description
    ) {}
}
