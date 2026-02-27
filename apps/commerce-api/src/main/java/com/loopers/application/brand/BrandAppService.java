package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 브랜드 도메인 Application Service.
 * 도메인 서비스를 호출하고 Model → Info 변환을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandAppService {

    private final BrandService brandService;

    /**
     * 새 브랜드를 등록한다.
     *
     * @param brandName   브랜드명
     * @param description 설명
     * @param address     주소
     * @return 생성된 브랜드 정보 DTO
     */
    @Transactional
    public BrandInfo createBrand(String brandName, String description, String address) {
        return BrandInfo.from(brandService.createBrand(brandName, description, address));
    }

    /**
     * 관리자용 전체 브랜드 목록을 조회한다 (삭제 포함).
     *
     * @return 전체 브랜드 정보 DTO 목록
     */
    public List<BrandInfo> findAllForAdmin() {
        return brandService.findAllForAdmin().stream()
                .map(BrandInfo::from)
                .toList();
    }

    /**
     * 고객에게 노출 가능한 브랜드를 ID로 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 브랜드 정보 DTO
     */
    public BrandInfo findVisibleById(String brandId) {
        return BrandInfo.from(brandService.findVisibleById(brandId));
    }

    /**
     * 고객에게 노출 가능한 브랜드 목록을 조회한다. 키워드가 있으면 검색한다.
     *
     * @param keyword 검색 키워드 (null이면 전체 조회)
     * @return 브랜드 정보 DTO 목록
     */
    public List<BrandInfo> findAllVisibleBrands(String keyword) {
        return brandService.findAllVisibleBrands(keyword).stream()
                .map(BrandInfo::from)
                .toList();
    }

    /**
     * 브랜드 정보를 수정한다.
     *
     * @param brandId     브랜드 ID
     * @param brandName   새 브랜드명
     * @param description 새 설명
     * @param address     새 주소
     * @return 수정된 브랜드 정보 DTO
     */
    @Transactional
    public BrandInfo updateBrand(String brandId, String brandName, String description, String address) {
        return BrandInfo.from(brandService.updateBrand(brandId, brandName, description, address));
    }

    /**
     * 브랜드를 소프트 삭제한다.
     *
     * @param brandId 삭제할 브랜드 ID
     */
    @Transactional
    public void deleteBrand(String brandId) {
        brandService.deleteBrand(brandId);
    }
}
