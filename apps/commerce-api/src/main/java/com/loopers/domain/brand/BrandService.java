package com.loopers.domain.brand;

import com.loopers.support.error.BrandErrorType;
import com.loopers.support.error.CoreException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 브랜드 도메인 서비스
 *
 * 브랜드 CRUD 및 상태 관리 비즈니스 로직을 담당한다.
 * 브랜드 삭제 시 연쇄 삭제(상품+재고)는 BrandAdminFacade에서 처리한다.
 */
@Component
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    /** 브랜드 생성 (초기 상태: ACTIVE) */
    @Transactional
    public Brand create(String name, String description) {
        Brand brand = Brand.create(name, description);
        return brandRepository.save(brand);
    }

    /**
     * 브랜드 단건 조회 (Admin용)
     * 삭제된 브랜드는 ALREADY_DELETED 예외를 던진다.
     */
    @Transactional(readOnly = true)
    public Brand getById(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(BrandErrorType.BRAND_NOT_FOUND));
        brand.assertNotDeleted();
        return brand;
    }

    /**
     * 활성 브랜드 단건 조회 (고객용)
     * 삭제/비활성 브랜드는 고객에게 "존재하지 않음"으로 처리한다 (404).
     */
    @Transactional(readOnly = true)
    public Brand getActiveBrand(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(BrandErrorType.BRAND_NOT_FOUND));
        // 고객 관점: 삭제/비활성 모두 "존재하지 않는 브랜드"
        if (brand.getDeletedAt() != null || !brand.isActive()) {
            throw new CoreException(BrandErrorType.BRAND_NOT_FOUND);
        }
        return brand;
    }

    /** 브랜드 정보 수정 */
    @Transactional
    public Brand update(Long id, String name, String description) {
        Brand brand = getById(id);
        brand.changeInfo(name, description);
        return brandRepository.save(brand);
    }

    /** 브랜드 소프트 삭제 (이미 삭제된 경우 409 Conflict) */
    @Transactional
    public void delete(Long id) {
        Brand brand = getById(id);
        brand.delete();
        brandRepository.save(brand);
    }

    /** 활성 브랜드 전체 조회 (고객용, 페이지네이션 미적용) */
    @Transactional(readOnly = true)
    public List<Brand> getAllActiveBrands() {
        return this.brandRepository.findAllActive();
    }

    /** 전체 브랜드 페이지 조회 (Admin용) */
    @Transactional(readOnly = true)
    public List<Brand> getAllBrands(int page, int size) {
        return this.brandRepository.findAll(page, size);
    }

    /** 전체 브랜드 수 조회 (Admin 페이지네이션 메타 정보용) */
    @Transactional(readOnly = true)
    public long countAllBrands() {
        return this.brandRepository.count();
    }

    /** ID 목록으로 브랜드 조회 (좋아요 목록용) */
    @Transactional(readOnly = true)
    public List<Brand> getBrandsByIds(List<Long> ids) {
        return this.brandRepository.findAllByIdIn(ids);
    }

    /** 브랜드 상태 변경 (ACTIVE ↔ INACTIVE) */
    @Transactional
    public Brand changeStatus(Long id, BrandStatus status) {
        Brand brand = getById(id);
        brand.changeStatus(status);
        return brandRepository.save(brand);
    }
}
