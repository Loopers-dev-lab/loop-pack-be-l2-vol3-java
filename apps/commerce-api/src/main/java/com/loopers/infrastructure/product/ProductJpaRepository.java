package com.loopers.infrastructure.product;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {
    List<ProductJpaEntity> findByDeletedFalse(Sort sort);

    @Query("SELECT p FROM ProductJpaEntity p LEFT JOIN LikeJpaEntity l ON p.id = l.productId WHERE p.deleted = false GROUP BY p.id ORDER BY COUNT(l.id) DESC")
    List<ProductJpaEntity> findAllOrderByLikesDescAndDeletedFalse();

    List<ProductJpaEntity> findByBrandIdAndDeletedFalse(Long brandId);

    Optional<ProductJpaEntity> findByIdAndDeletedFalse(Long id);
}
