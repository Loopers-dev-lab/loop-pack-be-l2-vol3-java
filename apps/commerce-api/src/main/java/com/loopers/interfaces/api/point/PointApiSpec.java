package com.loopers.interfaces.api.point;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Point API", description = "포인트 API")
public interface PointApiSpec {

    @Operation(summary = "포인트 잔액 조회", description = "본인의 포인트 잔액을 조회합니다.")
    ApiResponse<PointResponse.PointBalanceResponse> getMyPoints(@AuthUser User user);
}
