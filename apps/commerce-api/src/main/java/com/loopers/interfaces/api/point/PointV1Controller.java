package com.loopers.interfaces.api.point;

import com.loopers.application.point.UserPointResult;
import com.loopers.application.point.UserPointService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 사용자 API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users/me/points")
public class PointV1Controller {

    private final UserPointService userPointService;

    /**
     * 내 포인트 조회.
     */
    @GetMapping
    public ApiResponse<PointV1Dto.PointResponseDto> getMyPoint(@UserId Long userId) {
        UserPointResult result = userPointService.getPoint(userId);
        return ApiResponse.success(PointV1Dto.PointResponseDto.from(result));
    }

    /**
     * 포인트 충전.
     */
    @PostMapping("/charge")
    public ApiResponse<PointV1Dto.PointResponseDto> chargePoint(
            @UserId Long userId,
            @RequestBody PointV1Dto.ChargePointRequestDto request) {
        UserPointResult result = userPointService.charge(userId, request.amount());
        return ApiResponse.success(PointV1Dto.PointResponseDto.from(result));
    }
}
