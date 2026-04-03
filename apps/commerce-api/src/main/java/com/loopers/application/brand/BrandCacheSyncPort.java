package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;

import java.util.UUID;

public interface BrandCacheSyncPort {

    void registerUpsert(Brand brand);

    void registerDelete(UUID brandId);
}
