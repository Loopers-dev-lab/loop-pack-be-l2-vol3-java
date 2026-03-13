package com.loopers.domain.brand;

import com.loopers.domain.product.ProductService;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * 브랜드 도메인 서비스.
 * 브랜드 CRUD, 고객용/관리자용 조회, 소프트 삭제를 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;
    private final ProductService productService;

    /**
     * 새 브랜드를 등록한다.
     *
     * @param brandName   브랜드명
     * @param description 설명
     * @param address     주소
     * @return 생성된 브랜드 정보 DTO
     */
    @Transactional
    public BrandModel createBrand(String brandName, String description, String address) {
        BrandModel brand = BrandModel.create(brandName, description, address);
        return brandRepository.save(brand);
    }

    /**
     * 관리자용 전체 브랜드 목록을 조회한다 (삭제 포함).
     *
     * @return 전체 브랜드 엔티티 목록
     */
    public List<BrandModel> findAllForAdmin() {
        return brandRepository.findAll();
    }

    /**
     * 브랜드 ID로 브랜드를 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 브랜드 엔티티
     * @throws CoreException 브랜드가 존재하지 않을 때 (BRAND_NOT_FOUND)
     */
    /**
     * 브랜드 ID 목록으로 브랜드를 일괄 조회한다.
     *
     * @param brandIds 브랜드 ID 목록
     * @return 브랜드 엔티티 목록
     */
    public List<BrandModel> findAllByIds(Collection<Long> brandIds) {
        return brandRepository.findAllByIds(brandIds);
    }

    @Cacheable(cacheNames = "brandDetail", key = "#brandId")
    public BrandModel findById(Long brandId) {
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
    }

    /**
     * 고객에게 노출 가능한 브랜드를 ID로 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 브랜드 정보 DTO
     * @throws CoreException 브랜드가 존재하지 않거나 비노출 상태일 때 (BRAND_NOT_FOUND)
     */
    public BrandModel findVisibleById(Long brandId) {
        BrandModel brand = findById(brandId);
        if (!brand.isVisibleForCustomer()) {
            throw new CoreException(ErrorType.BRAND_NOT_FOUND);
        }
        return brand;
    }

    /**
     * 고객에게 노출 가능한 브랜드 목록을 조회한다. 키워드가 있으면 검색한다.
     *
     * @param keyword 검색 키워드 (null이면 전체 조회)
     * @return 브랜드 정보 DTO 목록
     */
    public List<BrandModel> findAllVisibleBrands(String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return brandRepository.findAllByKeyword(keyword);
        }
        return brandRepository.findAllByDelYnAndDisplayStatus("N", DisplayStatus.ACTIVE);
    }

    /**
     * 브랜드 정보를 수정한다.
     *
     * @param brandId     브랜드 ID
     * @param brandName   새 브랜드명
     * @param description 새 설명
     * @param address     새 주소
     * @return 수정된 브랜드 정보 DTO
     * @throws CoreException 브랜드가 존재하지 않을 때 (BRAND_NOT_FOUND)
     */
    @CacheEvict(cacheNames = "brandDetail", key = "#brandId")
    @Transactional
    public BrandModel updateBrand(Long brandId, String brandName, String description, String address) {
        BrandModel brand = findById(brandId);
        brand.updateInfo(brandName, description, address);
        return brand;
    }

    /**
     * 브랜드를 소프트 삭제한다. 소속 상품도 연쇄 소프트 삭제된다.
     *
     * @param brandId 삭제할 브랜드 ID
     * @throws CoreException 브랜드가 존재하지 않을 때 (BRAND_NOT_FOUND)
     */
    @CacheEvict(cacheNames = "brandDetail", key = "#brandId")
    @Transactional
    public void deleteBrand(Long brandId) {
        BrandModel brand = findById(brandId);
        brand.softDelete();
        productService.softDeleteByBrandId(brandId);
    }
}
