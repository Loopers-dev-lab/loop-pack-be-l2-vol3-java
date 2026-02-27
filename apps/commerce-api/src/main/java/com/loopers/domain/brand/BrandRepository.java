package com.loopers.domain.brand;

import com.loopers.support.enums.DisplayStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 브랜드 도메인 리포지토리 인터페이스.
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의하며, 인프라스트럭처 계층에서 구현한다.
 */
public interface BrandRepository {

    /**
     * 브랜드를 저장한다.
     *
     * @param brand 저장할 브랜드 엔티티
     * @return 저장된 브랜드 엔티티
     */
    BrandModel save(BrandModel brand);

    /**
     * 브랜드 ID로 브랜드를 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 브랜드 (Optional)
     */
    Optional<BrandModel> findById(String brandId);

    /**
     * 전체 브랜드 목록을 조회한다 (관리자용, 삭제 포함).
     *
     * @return 전체 브랜드 목록
     */
    List<BrandModel> findAll();

    /**
     * 삭제 여부와 노출 상태로 브랜드를 조회한다.
     *
     * @param delYn  삭제 여부 ("N": 미삭제)
     * @param status 노출 상태
     * @return 조건에 맞는 브랜드 목록
     */
    List<BrandModel> findAllByDelYnAndDisplayStatus(String delYn, DisplayStatus status);

    /**
     * 키워드로 활성 브랜드를 검색한다.
     *
     * @param keyword 검색 키워드
     * @return 키워드에 매칭되는 브랜드 목록
     */
    List<BrandModel> findAllByKeyword(String keyword);

    /**
     * 브랜드 ID 목록으로 브랜드를 일괄 조회한다.
     *
     * @param brandIds 브랜드 ID 목록
     * @return 해당 브랜드 목록
     */
    List<BrandModel> findAllByIds(Collection<String> brandIds);
}
