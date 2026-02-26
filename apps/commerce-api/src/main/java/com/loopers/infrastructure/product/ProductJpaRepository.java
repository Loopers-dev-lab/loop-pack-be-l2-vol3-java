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
    @Query("""
            update Product p
            set p.stockQuantity = p.stockQuantity - :quantity
            where p.id = :productId
              and p.deletedAt is null
              and p.visibility = :visibility
              and p.stockQuantity >= :quantity
            """)
    int decreaseStockIfEnough(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity,
            @Param("visibility") Product.Visibility visibility
    );
}
