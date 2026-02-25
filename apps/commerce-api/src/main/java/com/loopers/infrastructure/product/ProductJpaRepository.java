package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {

    @Query("SELECT p FROM ProductModel p JOIN FETCH p.brand WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductModel p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductModel> findByIdForUpdate(Long id);

    @Query(
        value = "SELECT p FROM ProductModel p JOIN FETCH p.brand WHERE p.deletedAt IS NULL",
        countQuery = "SELECT COUNT(p) FROM ProductModel p WHERE p.deletedAt IS NULL"
    )
    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);

    @Query(
        value = "SELECT p FROM ProductModel p JOIN FETCH p.brand LEFT JOIN LikeModel l ON l.product = p WHERE p.deletedAt IS NULL GROUP BY p ORDER BY COUNT(l) DESC",
        countQuery = "SELECT COUNT(p) FROM ProductModel p WHERE p.deletedAt IS NULL"
    )
    Page<ProductModel> findAllOrderByLikesDesc(Pageable pageable);
}
