package com.loopers.infrastructure.product;

import com.loopers.domain.product.ImageType;
import com.loopers.domain.product.ProductImageModel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageJpaRepository extends JpaRepository<ProductImageModel, Long> {

    List<ProductImageModel> findAllByProductIdAndDeletedAtIsNullOrderBySortOrder(Long productId);

    List<ProductImageModel> findAllByProductIdAndImageTypeAndDeletedAtIsNullOrderBySortOrder(
            Long productId, ImageType imageType);

    List<ProductImageModel> findAllByProductIdAndDeletedAtIsNull(Long productId);
}
