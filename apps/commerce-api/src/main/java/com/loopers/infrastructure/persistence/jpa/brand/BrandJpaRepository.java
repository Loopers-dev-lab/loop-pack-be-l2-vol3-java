package com.loopers.infrastructure.persistence.jpa.brand;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Brand JPA Repository.
 * Spring Data JPA를 사용한 영속성 계층.
 */
public interface BrandJpaRepository extends JpaRepository<BrandJpaEntity, Long> {

    Optional<BrandJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<BrandJpaEntity> findAllByDeletedAtIsNull();
}