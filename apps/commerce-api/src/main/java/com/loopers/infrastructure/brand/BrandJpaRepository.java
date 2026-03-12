package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandJpaRepository extends JpaRepository<BrandModel, Long> {
    Optional<BrandModel> findByIdAndDeletedAtIsNull(Long id);

    Optional<BrandModel> findByNameAndDeletedAtIsNull(String name);

    Page<BrandModel> findAllByDeletedAtIsNull(Pageable pageable);

    List<BrandModel> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
}
