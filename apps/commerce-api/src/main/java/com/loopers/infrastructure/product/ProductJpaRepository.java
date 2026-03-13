package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {

    // AS-IS: Like 테이블 JOIN + COUNT + GROUP BY → 매 요청마다 집계 연산 발생
    // TO-BE: 비정규화된 likesCount 컬럼 기반 정렬 → 인덱스 활용, 집계 연산 제거
    List<Product> findByDeletedAtIsNull(Sort sort);
}
