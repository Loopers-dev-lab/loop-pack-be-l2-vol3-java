package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandFacade {
    private final BrandService brandService;

    // 브랜드 상세 조회
    public BrandInfo findById(Long id){
        Brand brand = brandService.findById(id);
        return BrandInfo.from(brand);
    }

}
