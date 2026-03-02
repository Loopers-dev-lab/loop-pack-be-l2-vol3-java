package com.loopers.interfaces.api.point;

import com.loopers.application.point.PointFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PointController implements PointApiSpec {

    private final PointFacade pointFacade;

    public PointController(PointFacade pointFacade) {
        this.pointFacade = pointFacade;
    }

    @GetMapping("/api/v1/users/me/points")
    @Override
    public ApiResponse<PointResponse.PointBalanceResponse> getMyPoints(@AuthUser User user) {
        int balance = pointFacade.getMyPoints(user.getId());
        return ApiResponse.success(new PointResponse.PointBalanceResponse(user.getId(), balance));
    }
}
