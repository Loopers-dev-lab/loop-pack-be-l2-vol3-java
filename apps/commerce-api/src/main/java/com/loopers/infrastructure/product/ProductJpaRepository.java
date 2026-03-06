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

public interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID> {

    Optional<ProductEntity> findByIdAndDeletedAtIsNull(UUID id);

    List<ProductEntity> findAllByIdInAndDeletedAtIsNullOrderByIdAsc(List<UUID> ids);

    Page<ProductEntity> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ProductEntity> findAllByBrandIdAndDeletedAtIsNull(UUID brandId, Pageable pageable);

    Page<ProductEntity> findAllByBrandId(UUID brandId, Pageable pageable);

    @Query("SELECT p.id FROM ProductEntity p WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    List<UUID> findIdsByBrandIdAndDeletedAtIsNull(@Param("brandId") UUID brandId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update ProductEntity p
       set p.deletedAt = CURRENT_TIMESTAMP
     where p.brandId = :brandId
       and p.deletedAt is null
""")
    int softDeleteByBrandId(@Param("brandId") UUID brandId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProductEntity p
            set p.likeCount = case
                when p.likeCount + :delta < 0 then 0
                else p.likeCount + :delta
            end
            where p.id = :productId
              and p.deletedAt is null
            """)
    int updateLikeCount(@Param("productId") UUID productId, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ProductEntity p
            set p.stock = p.stock - :quantity
            where p.id = :productId
              and p.deletedAt is null
              and p.stock >= :quantity
            """)
    int decreaseStockAtomically(@Param("productId") UUID productId, @Param("quantity") int quantity);
}
