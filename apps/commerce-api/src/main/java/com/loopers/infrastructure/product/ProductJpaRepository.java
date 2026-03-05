package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findByDeletedFalse(Sort sort);

    @Query("SELECT p FROM Product p WHERE p.deleted = false ORDER BY p.likeCount DESC")
    List<Product> findAllOrderByLikesDescAndDeletedFalse();

    List<Product> findByBrandIdAndDeletedFalse(Long brandId);

    Optional<Product> findByIdAndDeletedFalse(Long id);

    List<Product> findByIdInAndDeletedFalse(List<Long> productIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.deleted = false")
    Optional<Product> findByIdWithLock(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Product p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id AND p.deleted = false")
    int increaseLikeCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Product p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :id AND p.deleted = false")
    int decreaseLikeCount(@Param("id") Long id);

}
