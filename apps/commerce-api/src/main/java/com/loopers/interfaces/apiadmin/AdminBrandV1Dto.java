package com.loopers.interfaces.apiadmin;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.enums.DisplayStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 관리자 브랜드 API의 요청/응답 DTO를 정의하는 클래스.
 */
public class AdminBrandV1Dto {

    /**
     * 브랜드 생성 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateBrandRequest {
        @NotBlank(message = "브랜드 이름은 필수입니다")
        private String brandName;
        private String description;
        private String address;
    }

    /**
     * 브랜드 수정 요청 DTO.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateBrandRequest {
        @NotBlank(message = "브랜드 이름은 필수입니다")
        private String brandName;
        private String description;
        private String address;
    }

    /**
     * 관리자용 브랜드 응답 DTO.
     *
     * <p>삭제 여부, 삭제 일시 등 관리자에게 필요한 추가 정보를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class AdminBrandResponse {
        private Long brandId;
        private String brandName;
        private String description;
        private String address;
        private DisplayStatus displayStatus;
        private String delYn;
        private ZonedDateTime deletedAt;
        private ZonedDateTime createdAt;

        /**
         * {@link BrandModel}을 관리자 브랜드 응답 DTO로 변환하는 정적 팩토리 메서드.
         *
         * @param model 브랜드 도메인 모델
         * @return 변환된 관리자 브랜드 응답 DTO
         */
        public static AdminBrandResponse from(BrandModel model) {
            return AdminBrandResponse.builder()
                    .brandId(model.getBrandId())
                    .brandName(model.getBrandName())
                    .description(model.getDescription())
                    .address(model.getAddress())
                    .displayStatus(model.getDisplayStatus())
                    .delYn(model.getDelYn())
                    .deletedAt(model.getDeletedAt())
                    .createdAt(model.getCreatedAt())
                    .build();
        }
    }
}
