package com.loopers.interfaces.apiadmin;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductRevisionInfo;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.enums.ProductRevisionAction;
import com.loopers.support.enums.ProductSaleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 상품 API의 요청/응답 DTO를 정의하는 클래스.
 */
public class AdminProductV1Dto {

    /**
     * 상품 생성 요청 DTO.
     *
     * <p>상품명, 브랜드 ID, 가격, 설명, 이미지 URL, 초기 재고를 포함한다.</p>
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateProductRequest {
        @NotBlank(message = "상품 이름은 필수입니다")
        private String productName;
        @NotBlank(message = "브랜드 ID는 필수입니다")
        private String brandId;
        @Positive(message = "가격은 0보다 커야 합니다")
        private BigDecimal price;
        private String description;
        private String imageUrl;
        @Min(value = 0, message = "초기 재고는 0 이상이어야 합니다")
        private int initialStock;
    }

    /**
     * 상품 수정 요청 DTO.
     *
     * <p>상품명, 가격, 설명, 이미지 URL을 포함한다.</p>
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateProductRequest {
        @NotBlank(message = "상품 이름은 필수입니다")
        private String productName;
        @Positive(message = "가격은 0보다 커야 합니다")
        private BigDecimal price;
        private String description;
        private String imageUrl;
    }

    /**
     * 관리자용 상품 응답 DTO.
     *
     * <p>노출 상태, 판매 상태, 삭제 여부, 가용 재고, 리비전 시퀀스 등 관리자에게 필요한 상세 정보를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class AdminProductResponse {
        private String productId;
        private String brandId;
        private String productName;
        private String description;
        private BigDecimal price;
        private DisplayStatus displayStatus;
        private ProductSaleStatus saleStatus;
        private String delYn;
        private int availableStock;
        private Long revisionSeq;

        /**
         * {@link ProductInfo}를 관리자 상품 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param info 상품 정보 DTO
         * @return 변환된 관리자 상품 응답 DTO
         */
        public static AdminProductResponse from(ProductInfo info) {
            return AdminProductResponse.builder()
                    .productId(info.getProductId())
                    .brandId(info.getBrandId())
                    .productName(info.getProductName())
                    .description(info.getDescription())
                    .price(info.getPrice())
                    .displayStatus(info.getDisplayStatus())
                    .saleStatus(info.getSaleStatus())
                    .availableStock(info.getAvailableStock())
                    .revisionSeq(info.getRevisionSeq())
                    .build();
        }
    }

    /**
     * 상품 변경 이력(revision) 응답 DTO.
     *
     * <p>변경 액션, 변경자, 변경 사유, 변경 전후 스냅샷 정보를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class RevisionResponse {
        private String productId;
        private Long revisionSeq;
        private ProductRevisionAction action;
        private String changedBy;
        private String changeReason;
        private String beforeSnapshot;
        private String afterSnapshot;
        private LocalDateTime createdAt;

        /**
         * {@link ProductRevisionInfo}를 상품 변경 이력 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param info 상품 변경 이력 정보 DTO
         * @return 변환된 상품 변경 이력 응답 DTO
         */
        public static RevisionResponse from(ProductRevisionInfo info) {
            return RevisionResponse.builder()
                    .productId(info.getProductId())
                    .revisionSeq(info.getRevisionSeq())
                    .action(info.getAction())
                    .changedBy(info.getChangedBy())
                    .changeReason(info.getChangeReason())
                    .beforeSnapshot(info.getBeforeSnapshot())
                    .afterSnapshot(info.getAfterSnapshot())
                    .createdAt(info.getCreatedAt())
                    .build();
        }
    }
}
