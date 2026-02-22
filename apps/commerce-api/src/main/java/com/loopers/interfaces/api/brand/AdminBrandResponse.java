package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.BrandStatus;

import java.time.ZonedDateTime;
import java.util.List;

/** 브랜드 어드민 API 응답 DTO */
public class AdminBrandResponse {

    /** 브랜드 상세 정보 (Admin용 - 상태, 생성/수정 시각 포함) */
    public record BrandDetail(
            Long id,
            String name,
            String description,
            BrandStatus status,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static BrandDetail from(BrandInfo info) {
            return new BrandDetail(
                    info.id(), info.name(), info.description(),
                    info.status(), info.createdAt(), info.updatedAt()
            );
        }
    }

    /** 브랜드 목록 + 페이지네이션 메타 정보 */
    public record BrandListResponse(
            List<BrandDetail> brands,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
