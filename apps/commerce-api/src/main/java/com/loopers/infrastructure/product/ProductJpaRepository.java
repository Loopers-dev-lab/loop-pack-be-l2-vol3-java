package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    List<Product> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);

    boolean existsByBrandIdAndNameAndDeletedAtIsNull(Long brandId, String name);

    boolean existsByBrandIdAndNameAndIdNotAndDeletedAtIsNull(Long brandId, String name, Long id);

    @Query("SELECT p.id FROM Product p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<Long> findIdsByBrandIdAndDeletedAtIsNull(@Param("brandId") Long brandId);

    @Modifying
    @Query("UPDATE Product p SET p.deletedAt = :deletedAt WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    void softDeleteAllByBrandId(@Param("brandId") Long brandId, @Param("deletedAt") ZonedDateTime deletedAt);

    // 비관적 락 - 재고 차감 전 행 잠금 (동시 주문 시 Lost Update 방지)
    // ORDER BY p.id ASC: DB가 PK 오름차순으로 스캔하며 락 획득 -> RDBMS 무관하게 데드락 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.deletedAt IS NULL ORDER BY p.id ASC")
    List<Product> findAllByIdsForUpdate(@Param("ids") List<Long> ids);

    // 원자적 좋아요 수 증가 (read-modify-write 대신 DB 레벨 UPDATE)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    void incrementLikeCount(@Param("id") Long id);

    // 원자적 좋아요 수 감소 (likeCount > 0 조건으로 음수 방지)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    void decrementLikeCount(@Param("id") Long id);
}
