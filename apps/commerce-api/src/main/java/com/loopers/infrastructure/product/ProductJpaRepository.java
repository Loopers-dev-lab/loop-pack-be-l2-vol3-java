package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);

    List<ProductModel> findAllByDeletedAtIsNull();

    Page<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    List<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    List<ProductModel> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    Page<ProductModel> findAllByDeletedAtIsNullOrderByLikeCountDesc(Pageable pageable);

    Page<ProductModel> findAllByBrandIdAndDeletedAtIsNullOrderByLikeCountDesc(
            Long brandId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductModel p SET p.likeCount = p.likeCount - 1"
            + " WHERE p.id = :id AND p.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);
}
