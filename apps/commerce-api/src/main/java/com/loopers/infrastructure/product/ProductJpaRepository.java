package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<ProductModel, Long> {
    Optional<ProductModel> findByIdAndDeletedAtIsNull(Long id);

    Page<ProductModel> findAllByDeletedAtIsNull(Pageable pageable);

    List<ProductModel> findAllByDeletedAtIsNull();

    Page<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    List<ProductModel> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    List<ProductModel> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
}
