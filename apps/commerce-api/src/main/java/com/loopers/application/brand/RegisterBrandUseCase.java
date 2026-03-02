package com.loopers.application.brand;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 새로운 브랜드를 등록합니다.
 *
 * <p>브랜드명, 로고, 설명을 받아 브랜드를 생성하고 등록 결과를 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class RegisterBrandUseCase {

    private final BrandService brandService;

    /**
     * @param name        브랜드명
     * @param logoUrl     로고 URL
     * @param description 브랜드 설명
     * @return 등록된 브랜드 정보
     */
    public BrandResult execute(String name, String logoUrl, String description) {
        Brand brand = brandService.create(name, logoUrl, description);
        return BrandResult.from(brand);
    }
}
