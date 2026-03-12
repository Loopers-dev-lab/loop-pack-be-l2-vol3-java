package com.loopers.domain.product;

import java.util.List;
import java.util.Optional;

/**
 * 상품 Repository 인터페이스.
 * 순수 Java 인터페이스로 Spring/JPA 의존성 없음.
 * 구현체는 Infrastructure Layer에 위치.
 */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Optional<Product> findByIdActive(Long id);

    Optional<Product> findByIdWithLock(Long id);

    List<Product> findAllActive(ProductSort sort, int offset, int limit);

    List<Product> findAllByBrandIdActive(Long brandId, ProductSort sort, int offset, int limit);

    List<Product> findAllByBrandIdActive(Long brandId);

    List<Product> findAllByIds(List<Long> ids);

    long countActive();

    long countByBrandIdActive(Long brandId);
}
