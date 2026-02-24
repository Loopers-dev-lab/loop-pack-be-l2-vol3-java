package com.loopers.domain.brand;

import java.util.Optional;

/**
 * 브랜드 영속성 인터페이스.
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface BrandRepository {

    Optional<BrandModel> findById(Long id);

    Optional<BrandModel> findByIdAndNotDeleted(Long id);

    BrandModel save(BrandModel brand);
}
