package com.loopers.infrastructure.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByReferenceIdAndDeletedAtIsNull(UUID referenceId);

    List<ProductEntity> findAllByReferenceIdInAndDeletedAtIsNullOrderByIdAsc(List<UUID> referenceIds);

    Page<ProductEntity> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductEntity> findAllByBrandReferenceIdAndDeletedAtIsNull(UUID brandReferenceId, Pageable pageable);

    Page<ProductEntity> findAllByBrandReferenceId(UUID brandReferenceId, Pageable pageable);

    @Query("SELECT p.referenceId FROM ProductEntity p WHERE p.brandReferenceId = :brandReferenceId AND p.deletedAt IS NULL")
    List<UUID> findReferenceIdsByBrandReferenceIdAndDeletedAtIsNull(@Param("brandReferenceId") UUID brandReferenceId);

    Optional<ProductEntity> findByReferenceId(UUID referenceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update ProductEntity p
       set p.deletedAt = CURRENT_TIMESTAMP
     where p.brandReferenceId = :brandReferenceId
       and p.deletedAt is null
""")
    int softDeleteByBrandReferenceId(@Param("brandReferenceId") UUID brandReferenceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProductEntity p
            set p.likeCount = case
                when p.likeCount + :delta < 0 then 0
                else p.likeCount + :delta
            end
            where p.referenceId = :productId
              and p.deletedAt is null
            """)
    int updateLikeCount(@Param("productId") UUID productId, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProductEntity p
            set p.stock = p.stock - :quantity
            where p.referenceId = :productId
              and p.deletedAt is null
              and p.stock >= :quantity
            """)
    int decreaseStockAtomically(@Param("productId") UUID productId, @Param("quantity") int quantity);
}
