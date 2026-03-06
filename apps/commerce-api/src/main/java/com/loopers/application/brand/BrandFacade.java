package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 브랜드 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → BrandInfo 변환.
 */
@Service
public class BrandFacade {

    private final BrandService brandService;

    public BrandFacade(BrandService brandService) {
        this.brandService = brandService;
    }

    @Transactional
    public BrandInfo registerBrand(String name) {
        BrandModel brand = brandService.registerBrand(name);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public Optional<BrandInfo> findById(Long id) {
        return brandService.findById(id).map(BrandInfo::from);
    }

    @Transactional(readOnly = true)
    public Optional<BrandInfo> findByIdAndNotDeleted(Long id) {
        return brandService.findByIdAndNotDeleted(id).map(BrandInfo::from);
    }

    @Transactional
    public BrandInfo renameBrand(Long id, String name) {
        BrandModel brand = brandService.renameBrand(id, name);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void deleteBrand(Long id) {
        brandService.deleteBrand(id);
    }
}
