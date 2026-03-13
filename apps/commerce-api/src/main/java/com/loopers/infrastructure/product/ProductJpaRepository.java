package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p LEFT JOIN com.loopers.domain.like.Like l ON l.productId = p.id " +
           "WHERE p.deletedAt IS NULL GROUP BY p ORDER BY COUNT(l) DESC")
    List<Product> findAllOrderByLikesDesc();
}
