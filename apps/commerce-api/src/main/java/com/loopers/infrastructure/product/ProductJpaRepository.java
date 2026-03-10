package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);
    List<Product> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE products
            SET stock_quantity = stock_quantity - :quantity
            WHERE id = :productId
              AND deleted_at IS NULL
              AND visibility = 'VISIBLE'
              AND stock_quantity >= :quantity
            """, nativeQuery = true)
    int decreaseStockIfEnough(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity
    );

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :productId")
    int increaseLikeCount(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.likeCount = p.likeCount - 1 WHERE p.id = :productId AND p.likeCount > 0")
    int decreaseLikeCount(@Param("productId") Long productId);
}
