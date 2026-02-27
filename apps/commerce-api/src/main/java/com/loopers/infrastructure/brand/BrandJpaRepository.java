package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.enums.DisplayStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 브랜드 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드(save, findById, findAll, delete 등)가 자동 제공되며,
 * 메서드 이름 규칙 기반의 쿼리 메서드를 추가로 정의한다.</p>
 */
public interface BrandJpaRepository extends JpaRepository<BrandModel, String> {

    /**
     * 삭제 여부와 노출 상태로 브랜드 목록을 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: 메서드 이름으로부터 자동 생성되는 쿼리를 사용한다.</p>
     *
     * @param delYn  삭제 여부 ("N": 미삭제, "Y": 삭제)
     * @param status 노출 상태 (ACTIVE, HIDDEN)
     * @return 조건에 부합하는 브랜드 목록
     */
    List<BrandModel> findAllByDelYnAndDisplayStatus(String delYn, DisplayStatus status);

    /**
     * 브랜드명 키워드(대소문자 무시)와 삭제 여부, 노출 상태로 브랜드 목록을 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: LIKE '%keyword%' (대소문자 무시) 조건으로 검색한다.</p>
     *
     * @param keyword 검색 키워드 (브랜드명에 포함)
     * @param delYn   삭제 여부 ("N": 미삭제)
     * @param status  노출 상태 (ACTIVE)
     * @return 조건에 부합하는 브랜드 목록
     */
    List<BrandModel> findAllByBrandNameContainingIgnoreCaseAndDelYnAndDisplayStatus(
            String keyword, String delYn, DisplayStatus status);
}
