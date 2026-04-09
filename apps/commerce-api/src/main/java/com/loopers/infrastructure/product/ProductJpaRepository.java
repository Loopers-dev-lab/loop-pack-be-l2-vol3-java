package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
        value = "SELECT p FROM ProductModel p JOIN FETCH p.brand WHERE p.deletedAt IS NULL AND p.brand.id = :brandId",
        countQuery = "SELECT COUNT(p) FROM ProductModel p WHERE p.deletedAt IS NULL AND p.brand.id = :brandId"
    )
    Page<ProductModel> findAllByDeletedAtIsNullAndBrandId(@Param("brandId") Long brandId, Pageable pageable);

    @Query(
        value = "SELECT p FROM ProductModel p JOIN FETCH p.brand WHERE p.deletedAt IS NULL ORDER BY p.likeCount DESC, p.id DESC",
        countQuery = "SELECT COUNT(p) FROM ProductModel p WHERE p.deletedAt IS NULL"
    )
    Page<ProductModel> findAllOrderByLikeCountDesc(Pageable pageable);

    @Query(
        value = "SELECT p FROM ProductModel p JOIN FETCH p.brand WHERE p.deletedAt IS NULL AND p.brand.id = :brandId ORDER BY p.likeCount DESC, p.id DESC",
        countQuery = "SELECT COUNT(p) FROM ProductModel p WHERE p.deletedAt IS NULL AND p.brand.id = :brandId"
    )
    Page<ProductModel> findAllByBrandIdOrderByLikeCountDesc(@Param("brandId") Long brandId, Pageable pageable);

    @Query("SELECT p FROM ProductModel p JOIN FETCH p.brand WHERE p.deletedAt IS NULL AND p.id IN :ids")
    List<ProductModel> findAllByIdInAndDeletedAtIsNull(@Param("ids") List<Long> ids);
}
