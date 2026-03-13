package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    // 기존엔 Like 테이블과 JOIN해서 COUNT로 정렬했는데
    // 데이터가 많아질수록 집계 비용이 커져서 likesCount 컬럼으로 대체
    List<Product> findByDeletedAtIsNull(Sort sort);
}
