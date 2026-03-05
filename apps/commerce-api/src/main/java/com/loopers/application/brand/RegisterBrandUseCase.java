package com.loopers.application.brand;

import com.loopers.application.brand.BrandCommand.CreateBrandCommand;
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
     * @param command 브랜드 생성 커맨드
     * @return 등록된 브랜드 정보
     */
    public BrandResult execute(CreateBrandCommand command) {
        Brand brand = brandService.create(command.toNewBrand());
        return BrandResult.from(brand);
    }
}
