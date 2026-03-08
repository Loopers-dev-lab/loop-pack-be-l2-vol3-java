package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    List<Product> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.deletedAt = CURRENT_TIMESTAMP, p.updatedAt = CURRENT_TIMESTAMP WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    void softDeleteAllByBrandId(@Param("brandId") Long brandId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1, "
         + "p.updatedAt = CURRENT_TIMESTAMP "
         + "WHERE p.id = :id AND p.deletedAt IS NULL")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.likeCount = p.likeCount - 1, "
         + "p.updatedAt = CURRENT_TIMESTAMP "
         + "WHERE p.id = :id AND p.likeCount > 0 AND p.deletedAt IS NULL")
    int decrementLikeCount(@Param("id") Long id);

}
