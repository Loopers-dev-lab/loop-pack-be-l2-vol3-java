package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
    Page<Brand> findAllByDeletedAtIsNull(Pageable pageable);
    List<Brand> findAllByIdIn(Collection<Long> ids);
}
