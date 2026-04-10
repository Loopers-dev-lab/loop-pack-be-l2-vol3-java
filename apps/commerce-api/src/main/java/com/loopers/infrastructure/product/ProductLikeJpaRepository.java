package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductLikeModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductLikeJpaRepository extends JpaRepository<ProductLikeModel,Long> {
    Optional<ProductLikeModel> findByUserIdAndProductId(Long userId, Long productId);

    List<ProductLikeModel> findAllByUserId(Long userId);

    long countByProductId(Long productId);

    @Query("SELECT l.productId, COUNT(l) FROM ProductLikeModel l WHERE l.productId IN :productIds GROUP BY l.productId")
    List<Object[]> countByProductIdIn(@Param("productIds") List<Long> productIds);

    @Query("SELECT l.productId, COUNT(l) FROM ProductLikeModel l"
            + " WHERE MOD(l.productId, :divisor) = :remainder GROUP BY l.productId")
    List<Object[]> countByProductIdModulo(
            @Param("divisor") int divisor, @Param("remainder") int remainder);
}
