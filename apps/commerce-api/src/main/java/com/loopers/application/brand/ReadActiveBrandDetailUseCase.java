package com.loopers.application.brand;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 활성 브랜드의 상세 정보를 조회합니다.
 *
 * <p>삭제되지 않은 브랜드만 조회 가능하며, 존재하지 않거나 삭제된 브랜드인 경우 예외가 발생합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadActiveBrandDetailUseCase {

    private final BrandRepository brandRepository;

    /**
     * @param brandId 브랜드 ID
     * @return 활성 브랜드 상세 정보
     */
    @Transactional(readOnly = true)
    public BrandResult execute(Long brandId) {
        return brandRepository.findByIdAndDeletedAtIsNull(brandId)
                .map(BrandResult::from)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
    }
}
