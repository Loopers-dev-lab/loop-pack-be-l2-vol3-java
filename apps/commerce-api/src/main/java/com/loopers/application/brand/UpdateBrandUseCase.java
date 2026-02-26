package com.loopers.application.brand;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.BrandService;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 브랜드 정보를 수정합니다.
 *
 * <p>브랜드명, 로고, 설명을 변경하며, 존재하지 않는 브랜드인 경우 예외가 발생합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class UpdateBrandUseCase {

    private final BrandService brandService;

    /**
     * @param brandId 수정할 브랜드 ID
     * @param name 새 브랜드명
     * @param logoUrl 새 로고 URL
     * @param description 새 설명
     */
    public void execute(Long brandId, String name, String logoUrl, String description) {
        brandService.update(brandId, name, logoUrl, description);
    }
}
