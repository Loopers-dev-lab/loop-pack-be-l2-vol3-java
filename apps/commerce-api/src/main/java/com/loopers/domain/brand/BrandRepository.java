package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;

/**
 * 브랜드 Repository 인터페이스.
 * 순수 Java 인터페이스로 Spring/JPA 의존성 없음.
 * 구현체는 Infrastructure Layer에 위치.
 */
public interface BrandRepository {

    Brand save(Brand brand);

    Optional<Brand> findById(Long id);

    Optional<Brand> findByIdActive(Long id);

    List<Brand> findAllActive();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}