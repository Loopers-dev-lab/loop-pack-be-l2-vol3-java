package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.vo.ProductId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    Optional<ProductModel> findByProductId(ProductId productId);

    boolean existsByProductId(ProductId productId);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE products SET stock_quantity = stock_quantity - :quantity WHERE id = :productId AND stock_quantity >= :quantity",
            nativeQuery = true
    )
    int decreaseStockIfAvailable(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE products SET stock_quantity = stock_quantity + :quantity WHERE id = :productId",
            nativeQuery = true
    )
    void increaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Query(
            value = "SELECT * FROM products WHERE ref_brand_id = :brandId AND deleted_at IS NULL",
            nativeQuery = true
    )
    List<ProductModel> findActiveByRefBrandId(@Param("brandId") Long brandId);
}
