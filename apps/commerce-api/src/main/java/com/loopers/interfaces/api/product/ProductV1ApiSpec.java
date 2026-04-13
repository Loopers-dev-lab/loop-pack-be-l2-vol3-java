package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.dto.FindProductApiResDto;
import com.loopers.interfaces.api.product.dto.FindProductListApiResDto;
import com.loopers.support.enums.SortFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Product V1 API", description = "상품 조회 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "상품 목록을 조회합니다. 로그인 시 좋아요 여부가 포함됩니다.")
    ApiResponse<Page<FindProductListApiResDto>> findProductList(
            @Parameter(description = "로그인 ID (선택)") String loginId,
            @Parameter(description = "비밀번호 (선택)") String password,
            @Parameter(description = "브랜드 ID (선택)") Long brandId,
            @Parameter(description = "정렬 조건") SortFilter sortFilter,
            Pageable pageable
    );

    @Operation(summary = "상품 상세 조회", description = "상품 상세 정보를 조회합니다. 로그인 시 좋아요 여부가 포함됩니다.")
    ApiResponse<FindProductApiResDto> findProduct(
            @Parameter(description = "로그인 ID (선택)") String loginId,
            @Parameter(description = "비밀번호 (선택)") String password,
            @Parameter(description = "상품 ID", required = true) Long productId,
            @Parameter(hidden = true) HttpServletRequest request
    );
}
