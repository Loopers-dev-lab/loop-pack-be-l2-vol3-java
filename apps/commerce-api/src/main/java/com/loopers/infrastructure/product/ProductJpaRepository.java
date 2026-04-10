package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.deletedAt IS NULL ORDER BY p.id ASC")
    List<Product> findAllByIdsWithLock(@Param("ids") List<Long> ids);

    @Query("SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.id IN :ids AND p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)")
    List<Object[]> findAllByIds(@Param("ids") List<Long> ids);

    List<Product> findAllByDeletedAtIsNull();
    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    @Query("SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)")
    List<Object[]> findAllWithBrand();

    @Query("SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)")
    List<Object[]> findAllWithBrand(Sort sort);

    @Query("SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.brandId = :brandId AND p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)")
    List<Object[]> findAllByBrandIdWithBrand(@Param("brandId") Long brandId);

    // 페이지네이션 조회 (Sort는 Pageable에 내장하여 전달)
    @Query(value = "SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)",
        countQuery = "SELECT COUNT(p) FROM Product p WHERE p.deletedAt IS NULL")
    Page<Object[]> findAllWithBrandPaged(Pageable pageable);

    @Query(value = "SELECT p, b.name FROM Product p LEFT JOIN Brand b ON b.id = p.brandId"
        + " WHERE p.brandId = :brandId AND p.deletedAt IS NULL AND (b.deletedAt IS NULL OR b.id IS NULL)",
        countQuery = "SELECT COUNT(p) FROM Product p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    Page<Object[]> findAllByBrandIdWithBrandPaged(@Param("brandId") Long brandId, Pageable pageable);

    // likeCount atomic 증감 — 엔티티 로딩 없이 단일 UPDATE 문으로 실행
    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :productId AND p.deletedAt IS NULL")
    int incrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE Product p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :productId AND p.deletedAt IS NULL")
    int decrementLikeCount(@Param("productId") Long productId);
}
