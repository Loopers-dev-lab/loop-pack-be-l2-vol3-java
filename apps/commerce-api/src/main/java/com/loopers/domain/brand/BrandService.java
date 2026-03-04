package com.loopers.domain.brand;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 브랜드 도메인의 핵심 비즈니스 규칙을 담당하는 도메인 서비스.
 */
@DomainService
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    /**
     * 새로운 브랜드를 생성한다.
     *
     * @param name        브랜드명
     * @param logoUrl     로고 URL
     * @param description 브랜드 설명
     * @return 생성된 브랜드
     * @throws CoreException 동일한 브랜드명이 이미 존재하는 경우
     */
    @Transactional
    public Brand create(String name, String logoUrl, String description) {
        if (brandRepository.existsByNameAndDeletedAtIsNull(name)) {
            throw new CoreException(ErrorType.ALREADY_EXISTS_BRAND_NAME);
        }
        Brand brand = Brand.create(name, logoUrl, description);
        return brandRepository.save(brand);
    }

    /**
     * 활성 상태의 브랜드를 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 활성 브랜드
     * @throws CoreException 브랜드가 존재하지 않거나 삭제된 경우
     */
    public Brand getActiveBrand(Long brandId) {
        return brandRepository.findByIdAndDeletedAtIsNull(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
    }

    /**
     * 브랜드 ID 목록으로 활성 브랜드 맵을 조회한다.
     *
     * @param brandIds 조회할 브랜드 ID 목록
     * @return 브랜드 ID를 키로 하는 활성 브랜드 맵
     */
    public Map<Long, Brand> getActiveBrandMap(List<Long> brandIds) {
        return brandRepository.findAllByIdInAndDeletedAtIsNull(brandIds)
                .stream()
                .filter(brand -> !brand.isDeleted())
                .collect(Collectors.toMap(Brand::getId, Function.identity()));
    }

    /**
     * 브랜드 정보를 수정한다.
     *
     * @param brandId        수정할 브랜드 ID
     * @param newName        새 브랜드명
     * @param newLogoUrl     새 로고 URL
     * @param newDescription 새 설명
     * @return 수정된 브랜드
     * @throws CoreException 브랜드가 존재하지 않거나 중복된 브랜드명인 경우
     */
    @Transactional
    public Brand update(Long brandId, String newName, String newLogoUrl, String newDescription) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        if (brandRepository.existsByIdNotAndNameAndDeletedAtIsNull(brandId, newName)) {
            throw new CoreException(ErrorType.ALREADY_EXISTS_BRAND_NAME);
        }
        brand.update(newName, newLogoUrl, newDescription);
        return brand;
    }

    /**
     * 브랜드를 소프트 삭제한다.
     *
     * @param brandId 삭제할 브랜드 ID
     * @return 실제로 삭제가 수행되었으면 true, 이미 삭제된 상태면 false
     * @throws CoreException 브랜드가 존재하지 않는 경우
     */
    @Transactional
    public boolean delete(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
        if (brand.isDeleted()) {
            return false;
        }
        brand.delete();
        return true;
    }

    /**
     * 브랜드의 존재 여부를 검증한다.
     *
     * @param brandId 브랜드 ID
     * @throws CoreException 브랜드가 존재하지 않는 경우
     */
    public void validateBrandExists(Long brandId) {
        if (!brandRepository.existsById(brandId)) {
            throw new CoreException(ErrorType.BRAND_NOT_FOUND);
        }
    }

    /**
     * 활성 브랜드의 존재 여부를 검증한다.
     *
     * @param brandId 브랜드 ID
     * @throws CoreException 브랜드가 존재하지 않거나 삭제된 경우
     */
    public void validateActiveBrandExists(Long brandId) {
        if (!brandRepository.existsByIdAndDeletedAtIsNull(brandId)) {
            throw new CoreException(ErrorType.BRAND_NOT_FOUND);
        }
    }
}
