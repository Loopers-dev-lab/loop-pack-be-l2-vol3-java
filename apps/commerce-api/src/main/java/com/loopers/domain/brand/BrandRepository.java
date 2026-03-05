package com.loopers.domain.brand;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 브랜드 영속성 인터페이스.
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface BrandRepository {

    Optional<BrandModel> findById(Long id);

    Optional<BrandModel> findByIdAndNotDeleted(Long id);

    /**
     * 주어진 id 목록에 해당하는 미삭제 브랜드를 일괄 조회한다.
     * N+1 방지용 (예: 상품 목록의 brandId 일괄 조회).
     */
    List<BrandModel> findByIdAndNotDeletedIn(Collection<Long> ids);

    BrandModel save(BrandModel brand);
}
