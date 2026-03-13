package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.BrandModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 브랜드 API V1 요청/응답 DTO 모음.
 *
 * <p>브랜드 관련 REST API의 HTTP 요청 및 응답 데이터 구조를 정의한다.</p>
 */
public class BrandV1Dto {

    /**
     * 브랜드 조회 응답 DTO.
     *
     * <p>브랜드 ID, 이름, 설명, 주소, 첨부 파일 정보를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class BrandResponse {
        private Long brandId;
        private String brandName;
        private String description;
        private String address;
        private String attachFile;

        /**
         * {@link BrandModel}을 브랜드 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param model 변환할 브랜드 도메인 모델
         * @return 변환된 BrandResponse
         */
        public static BrandResponse from(BrandModel model) {
            return BrandResponse.builder()
                    .brandId(model.getBrandId())
                    .brandName(model.getBrandName())
                    .description(model.getDescription())
                    .address(model.getAddress())
                    .attachFile(model.getAttachFile())
                    .build();
        }
    }
}
