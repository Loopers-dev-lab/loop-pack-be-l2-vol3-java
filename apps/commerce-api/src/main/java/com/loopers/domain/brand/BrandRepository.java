package com.loopers.domain.brand;

import java.util.List;
import java.util.Optional;

/**
 * 브랜드 리포지토리 포트 (Domain Layer)
 *
 * 도메인이 인프라(JPA)에 의존하지 않도록 추상화한 인터페이스.
 * 실제 구현은 Infrastructure 계층의 BrandRepositoryImpl이 담당한다.
 */
public interface BrandRepository {
    Brand save(Brand brand);
    Optional<Brand> findById(Long id);
    List<Brand> findAll(int page, int size);
    long count();
    List<Brand> findAllActive();
}
