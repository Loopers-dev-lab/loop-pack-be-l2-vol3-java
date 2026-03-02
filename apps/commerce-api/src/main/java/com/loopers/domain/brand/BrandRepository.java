package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * 브랜드 도메인 리포지토리 인터페이스.
 */
public interface BrandRepository {

    /**
     * 브랜드를 저장한다.
     *
     * @param brand 저장할 브랜드
     * @return 저장된 브랜드
     */
    Brand save(Brand brand);

    /**
     * ID로 브랜드를 조회한다. 삭제 여부와 무관하게 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 브랜드 (존재하지 않으면 빈 Optional)
     */
    Optional<Brand> findById(Long brandId);

    /**
     * ID 목록에 해당하는 활성 브랜드를 모두 조회한다.
     *
     * @param brandIds 브랜드 ID 목록
     * @return 활성 브랜드 목록
     */
    List<Brand> findAllByIdInAndDeletedAtIsNull(List<Long> brandIds);

    /**
     * ID로 활성 브랜드를 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 활성 브랜드 (존재하지 않거나 삭제된 경우 빈 Optional)
     */
    Optional<Brand> findByIdAndDeletedAtIsNull(Long brandId);

    /**
     * 전체 브랜드를 페이징 조회한다.
     *
     * @param pageable 페이징 조건
     * @return 브랜드 슬라이스
     */
    Slice<Brand> findAllBy(Pageable pageable);

    /**
     * 브랜드 존재 여부를 확인한다. 삭제 여부와 무관하게 확인한다.
     *
     * @param brandId 브랜드 ID
     * @return 존재하면 true
     */
    boolean existsById(Long brandId);

    /**
     * 활성 브랜드 존재 여부를 확인한다.
     *
     * @param brandId 브랜드 ID
     * @return 활성 브랜드가 존재하면 true
     */
    boolean existsByIdAndDeletedAtIsNull(Long brandId);

    /**
     * 동일한 브랜드명의 활성 브랜드 존재 여부를 확인한다.
     *
     * @param name 브랜드명
     * @return 동일 이름의 활성 브랜드가 존재하면 true
     */
    boolean existsByNameAndDeletedAtIsNull(String name);

    /**
     * 특정 브랜드를 제외하고 동일한 브랜드명의 활성 브랜드 존재 여부를 확인한다.
     *
     * <p>브랜드 수정 시 이름 중복 검증에 사용한다.</p>
     *
     * @param brandId 제외할 브랜드 ID
     * @param name    브랜드명
     * @return 동일 이름의 활성 브랜드가 존재하면 true
     */
    boolean existsByIdNotAndNameAndDeletedAtIsNull(Long brandId, String name);
}
