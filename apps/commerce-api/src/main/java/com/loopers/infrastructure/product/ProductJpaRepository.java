package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

/**
 * 상품 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 *
 * <p>JpaRepository를 상속받아 기본 CRUD 메서드가 자동 제공되며,
 * 고객용 검색 쿼리와 관리자용 조회 쿼리를 정의한다.</p>
 */
public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    /**
     * 고객용 상품 목록을 조회한다.
     *
     * <p>조건: 미삭제(del_yn='N'), 노출 상태 ACTIVE, 판매 상태 ON_SALE.
     * 선택적으로 키워드(상품명 LIKE)와 브랜드 ID로 필터링한다.</p>
     *
     * @param keyword 검색 키워드 (null이면 전체)
     * @param brandId 브랜드 ID (null이면 전체)
     * @return 조건에 부합하는 상품 목록
     */
    @Query("SELECT p FROM ProductModel p " +
           "WHERE p.delYn = 'N' AND p.displayStatus = 'ACTIVE' AND p.saleStatus = 'ON_SALE' " +
           "AND (:keyword IS NULL OR p.productName LIKE %:keyword%) " +
           "AND (:brandId IS NULL OR p.brandId = :brandId)")
    List<ProductModel> findAllForCustomer(@Param("keyword") String keyword,
                                          @Param("brandId") Long brandId);

    /**
     * 브랜드 ID로 상품 목록을 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: 메서드 이름으로부터 자동 생성되는 쿼리를 사용한다.</p>
     *
     * @param brandId 브랜드 ID
     * @return 해당 브랜드의 상품 목록
     */
    List<ProductModel> findAllByBrandId(Long brandId);

    /**
     * 삭제 여부로 상품 목록을 조회한다.
     *
     * <p>Spring Data JPA 쿼리 메서드: 메서드 이름으로부터 자동 생성되는 쿼리를 사용한다.</p>
     *
     * @param delYn 삭제 여부 ("N": 미삭제, "Y": 삭제)
     * @return 조건에 부합하는 상품 목록
     */
    List<ProductModel> findAllByDelYn(String delYn);

    /**
     * 고객용 상품 목록을 페이징하여 조회한다.
     *
     * @param keyword  검색 키워드 (null이면 전체)
     * @param brandId  브랜드 ID (null이면 전체)
     * @param pageable 페이징/정렬 정보
     * @return 페이징된 상품 목록
     */
    @Query("SELECT p FROM ProductModel p " +
           "WHERE p.delYn = 'N' AND p.displayStatus = 'ACTIVE' AND p.saleStatus = 'ON_SALE' " +
           "AND (:keyword IS NULL OR p.productName LIKE %:keyword%) " +
           "AND (:brandId IS NULL OR p.brandId = :brandId)")
    Page<ProductModel> findAllForCustomerPaged(@Param("keyword") String keyword,
                                               @Param("brandId") Long brandId,
                                               Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductModel p WHERE p.productId = :productId")
    Optional<ProductModel> findByIdWithLock(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.productId = :productId")
    void incrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = GREATEST(p.likeCount - 1, 0) WHERE p.productId = :productId")
    void decrementLikeCount(@Param("productId") Long productId);

    /**
     * 상품의 like_count와 likes 테이블의 실제 좋아요 수가 불일치하는 상품 목록을 조회한다.
     *
     * @return [productId, currentCount, actualCount] 쌍 목록
     */
    @Query(value = "SELECT p.product_id, p.like_count AS currentCount, COUNT(l.product_id) AS actualCount " +
            "FROM products p " +
            "LEFT JOIN likes l ON p.product_id = l.product_id AND l.del_yn = 'N' " +
            "WHERE p.del_yn = 'N' " +
            "GROUP BY p.product_id, p.like_count " +
            "HAVING p.like_count != COUNT(l.product_id)",
            nativeQuery = true)
    List<Object[]> findLikeCountMismatchesRaw();

    @Modifying
    @Query("UPDATE ProductModel p SET p.likeCount = :likeCount WHERE p.productId = :productId")
    void updateLikeCount(@Param("productId") Long productId, @Param("likeCount") long likeCount);

}
