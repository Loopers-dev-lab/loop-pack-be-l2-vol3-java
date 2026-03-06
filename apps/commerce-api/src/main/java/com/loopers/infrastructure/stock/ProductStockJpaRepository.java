package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.ProductStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductStockJpaRepository extends JpaRepository<ProductStock, Long> {

    Optional<ProductStock> findByProductIdAndDeletedAtIsNull(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ProductStock ps WHERE ps.productId = :productId AND ps.deletedAt IS NULL")
    Optional<ProductStock> findByProductIdWithLock(@Param("productId") Long productId);

    List<ProductStock> findAllByProductIdInAndDeletedAtIsNull(Collection<Long> productIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductStock ps SET ps.deletedAt = CURRENT_TIMESTAMP, ps.updatedAt = CURRENT_TIMESTAMP WHERE ps.productId = :productId AND ps.deletedAt IS NULL")
    void softDeleteByProductId(@Param("productId") Long productId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductStock ps SET ps.deletedAt = CURRENT_TIMESTAMP, ps.updatedAt = CURRENT_TIMESTAMP WHERE ps.productId IN :productIds AND ps.deletedAt IS NULL")
    void softDeleteAllByProductIdIn(@Param("productIds") Collection<Long> productIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductStock ps SET ps.deletedAt = CURRENT_TIMESTAMP, ps.updatedAt = CURRENT_TIMESTAMP WHERE ps.productId IN (SELECT p.id FROM Product p WHERE p.brandId = :brandId) AND ps.deletedAt IS NULL")
    void softDeleteAllByBrandId(@Param("brandId") Long brandId);
}
