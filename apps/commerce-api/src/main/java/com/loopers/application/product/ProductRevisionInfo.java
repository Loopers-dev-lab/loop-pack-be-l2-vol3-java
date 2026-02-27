package com.loopers.application.product;

import com.loopers.domain.product.ProductRevisionModel;
import com.loopers.support.enums.ProductRevisionAction;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 상품 변경 이력 정보 DTO.
 * <p>
 * 도메인 모델({@link ProductRevisionModel})을 직접 노출하지 않고
 * interfaces 계층에 전달하기 위한 응답 객체이다.
 * </p>
 */
@Getter
@Builder
public class ProductRevisionInfo {
    private final String productId;
    private final Long revisionSeq;
    private final ProductRevisionAction action;
    private final String changedBy;
    private final String changeReason;
    private final String beforeSnapshot;
    private final String afterSnapshot;
    private final LocalDateTime createdAt;

    /**
     * ProductRevisionModel을 ProductRevisionInfo DTO로 변환한다.
     *
     * @param model 상품 변경 이력 엔티티
     * @return 변경 이력 정보 DTO
     */
    public static ProductRevisionInfo from(ProductRevisionModel model) {
        return ProductRevisionInfo.builder()
                .productId(model.getProductId())
                .revisionSeq(model.getRevisionSeq())
                .action(model.getAction())
                .changedBy(model.getChangedBy())
                .changeReason(model.getChangeReason())
                .beforeSnapshot(model.getBeforeSnapshot())
                .afterSnapshot(model.getAfterSnapshot())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
