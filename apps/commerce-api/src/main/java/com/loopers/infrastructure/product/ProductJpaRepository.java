package com.loopers.infrastructure.product;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {
    List<ProductJpaEntity> findAll(Sort sort);

    @Query("SELECT p FROM ProductJpaEntity p LEFT JOIN LikeJpaEntity l ON p.id = l.productId GROUP BY p.id ORDER BY COUNT(l.id) DESC")
    List<ProductJpaEntity> findAllOrderByLikesDesc();

    List<ProductJpaEntity> findByBrandId(Long brandId);
}
