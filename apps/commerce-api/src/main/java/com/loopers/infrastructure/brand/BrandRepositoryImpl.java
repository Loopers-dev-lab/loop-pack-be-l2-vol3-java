package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.enums.DisplayStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 도메인 {@link BrandRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link BrandJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository jpaRepository;

    /**
     * 브랜드를 저장한다.
     *
     * @param brand 저장할 브랜드 엔티티
     * @return 저장된 브랜드 엔티티 (ID가 자동 생성됨)
     */
    @Override
    public BrandModel save(BrandModel brand) {
        return jpaRepository.save(brand);
    }

    /**
     * 브랜드 ID로 브랜드를 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 브랜드 (Optional)
     */
    @Override
    public Optional<BrandModel> findById(Long brandId) {
        return jpaRepository.findById(brandId);
    }

    /**
     * 전체 브랜드 목록을 조회한다.
     *
     * @return 전체 브랜드 목록
     */
    @Override
    public List<BrandModel> findAll() {
        return jpaRepository.findAll();
    }

    /**
     * 삭제 여부와 노출 상태로 브랜드 목록을 조회한다.
     *
     * @param delYn  삭제 여부 ("N": 미삭제)
     * @param status 노출 상태 (ACTIVE, HIDDEN)
     * @return 조건에 부합하는 브랜드 목록
     */
    @Override
    public List<BrandModel> findAllByDelYnAndDisplayStatus(String delYn, DisplayStatus status) {
        return jpaRepository.findAllByDelYnAndDisplayStatus(delYn, status);
    }

    /**
     * 키워드로 활성 브랜드를 검색한다.
     *
     * <p>삭제되지 않고(del_yn='N') 노출 상태가 ACTIVE인 브랜드 중
     * 브랜드명에 키워드가 포함된 브랜드를 반환한다.</p>
     *
     * @param keyword 검색 키워드
     * @return 키워드에 해당하는 활성 브랜드 목록
     */
    @Override
    public List<BrandModel> findAllByKeyword(String keyword) {
        return jpaRepository.findAllByBrandNameContainingIgnoreCaseAndDelYnAndDisplayStatus(
                keyword, "N", DisplayStatus.ACTIVE);
    }

    /**
     * 브랜드 ID 목록으로 브랜드를 일괄 조회한다.
     *
     * @param brandIds 브랜드 ID 목록
     * @return 해당 브랜드 목록
     */
    @Override
    public List<BrandModel> findAllByIds(Collection<Long> brandIds) {
        return jpaRepository.findAllById(brandIds);
    }
}
