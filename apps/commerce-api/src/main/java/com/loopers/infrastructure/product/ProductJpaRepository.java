package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    List<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId);

    List<Product> findAllByBrandId(Long brandId);

    List<Product> findAllByIdInAndDeletedAtIsNull(Collection<Long> ids);

    List<Product> findAllByIdIn(Collection<Long> ids);

    Page<Product> findAllByBrandIdAndDeletedAtIsNull(Long brandId, Pageable pageable);

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsByNameAndDeletedAtIsNull(String name);
}
