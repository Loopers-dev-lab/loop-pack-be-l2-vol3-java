package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.enums.DisplayStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * 브랜드 정보 DTO.
 * 도메인 모델({@link BrandModel})을 직접 노출하지 않고 인터페이스 레이어에 전달하기 위한 응답 객체이다.
 */
@Getter
@Builder
public class BrandInfo {
    private final String brandId;
    private final String brandName;
    private final String description;
    private final String address;
    private final DisplayStatus displayStatus;
    private final String attachFile;
    private final String delYn;
    private final ZonedDateTime deletedAt;
    private final ZonedDateTime createdAt;

    /**
     * BrandModel을 BrandInfo DTO로 변환한다.
     *
     * @param model 변환할 브랜드 엔티티
     * @return 브랜드 정보 DTO
     */
    public static BrandInfo from(BrandModel model) {
        return BrandInfo.builder()
                .brandId(model.getBrandId())
                .brandName(model.getBrandName())
                .description(model.getDescription())
                .address(model.getAddress())
                .displayStatus(model.getDisplayStatus())
                .attachFile(model.getAttachFile())
                .delYn(model.getDelYn())
                .deletedAt(model.getDeletedAt())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
