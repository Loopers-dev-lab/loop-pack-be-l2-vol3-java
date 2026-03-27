package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {

    Optional<BrandModel> findByIdAndDeletedAtIsNull(Long id);

    Optional<BrandModel> findByNameAndDeletedAtIsNull(String name);

    Page<BrandModel> findAllByDeletedAtIsNull(Pageable pageable);

    List<BrandModel> findAllByIdIn(List<Long> ids);
}
