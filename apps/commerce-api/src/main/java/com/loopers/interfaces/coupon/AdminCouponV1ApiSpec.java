package com.loopers.interfaces.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.coupon.dto.AdminCouponV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Coupon V1 API", description = "쿠폰 관리자 API 입니다.")
public interface AdminCouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 등록",
        description = "새로운 쿠폰을 등록합니다."
    )
    ApiResponse<AdminCouponV1Dto.DetailResponse> register(
        @RequestBody(description = "쿠폰 등록 요청 정보") AdminCouponV1Dto.RegisterRequest request
    );

    @Operation(
        summary = "쿠폰 목록 조회",
        description = "쿠폰 목록을 페이지네이션으로 조회합니다."
    )
    ApiResponse<AdminCouponV1Dto.ListResponse> list(
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") int page,
        @Parameter(description = "페이지 크기", example = "20") int size
    );

    @Operation(
        summary = "쿠폰 상세 조회",
        description = "쿠폰 상세를 조회합니다."
    )
    ApiResponse<AdminCouponV1Dto.DetailResponse> getById(
        @Parameter(description = "쿠폰 ID", required = true) Long couponId
    );

    @Operation(
        summary = "쿠폰 수정",
        description = "쿠폰의 이름과 유효기간을 수정합니다."
    )
    ApiResponse<Object> update(
        @Parameter(description = "쿠폰 ID", required = true) Long couponId,
        @RequestBody(description = "쿠폰 수정 요청 정보") AdminCouponV1Dto.UpdateRequest request
    );

    @Operation(
        summary = "쿠폰 삭제",
        description = "쿠폰을 삭제합니다. (Soft Delete)"
    )
    ApiResponse<Object> delete(
        @Parameter(description = "쿠폰 ID", required = true) Long couponId
    );

    @Operation(
        summary = "쿠폰 발급 내역 조회",
        description = "해당 쿠폰의 발급 내역을 페이지네이션으로 조회합니다."
    )
    ApiResponse<AdminCouponV1Dto.IssueListResponse> issues(
        @Parameter(description = "쿠폰 ID", required = true) Long couponId,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") int page,
        @Parameter(description = "페이지 크기", example = "20") int size
    );
}
