package com.loopers.application.brand;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.brand.BrandCommand.UpdateBrandCommand;
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
     * @param command 브랜드 수정 커맨드
     */
    @Transactional
    public void execute(UpdateBrandCommand command) {
        brandService.update(command.toModifyBrand());
    }
}
