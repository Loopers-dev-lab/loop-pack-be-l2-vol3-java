package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findByDeletedFalse(Sort sort);

    @Query("SELECT p FROM Product p LEFT JOIN Like l ON p.id = l.productId WHERE p.deleted = false GROUP BY p.id ORDER BY COUNT(l.id) DESC")
    List<Product> findAllOrderByLikesDescAndDeletedFalse();

    List<Product> findByBrandIdAndDeletedFalse(Long brandId);

    Optional<Product> findByIdAndDeletedFalse(Long id);

    List<Product> findByIdInAndDeletedFalse(List<Long> productIds);
}
