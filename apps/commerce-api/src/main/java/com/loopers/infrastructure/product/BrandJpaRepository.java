package com.loopers.infrastructure.product;

import com.loopers.domain.product.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandJpaRepository extends JpaRepository<Brand, Long> {
}
