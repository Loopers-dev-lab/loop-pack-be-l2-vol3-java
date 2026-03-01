package com.loopers.application.brand;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 브랜드 상세 정보를 조회합니다.
 *
 * <p>삭제된 브랜드도 조회 가능하며, 존재하지 않는 브랜드인 경우 예외가 발생합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadBrandDetailUseCase {

    private final BrandRepository brandRepository;

    /**
     * @param brandId 브랜드 ID
     * @return 브랜드 상세 정보
     */
    @Transactional(readOnly = true)
    public BrandResult execute(Long brandId) {
        return brandRepository.findById(brandId)
                .map(BrandResult::from)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
    }
}
