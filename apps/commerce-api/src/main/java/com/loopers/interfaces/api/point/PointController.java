package com.loopers.interfaces.api.point;

import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointService;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PointController implements PointApiSpec {

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping("/api/v1/users/me/points")
    @Override
    public ApiResponse<PointResponse.PointBalanceResponse> getMyPoints(@AuthUser User user) {
        PointAccount account = pointService.getAccount(user.getId());
        return ApiResponse.success(new PointResponse.PointBalanceResponse(
                user.getId(), account.getBalance()));
    }
}
