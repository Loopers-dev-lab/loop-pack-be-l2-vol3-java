package com.loopers.interfaces.api.user;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.RegisterUserCommand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserV1Controller {

    private final UserFacade userFacade;

    @PostMapping("/register")
    public ApiResponse<UserV1Dto.RegisterResponse> register(@RequestBody UserV1Dto.RegisterRequest request) {
        RegisterUserCommand command = new RegisterUserCommand(
                request.loginId(), request.password(), request.name(),
                request.birthDate(), request.email()
        );
        UserInfo userInfo = userFacade.register(command);

        UserV1Dto.RegisterResponse response = new UserV1Dto.RegisterResponse(
            userInfo.id(),
            userInfo.loginId(),
            userInfo.name(),
            userInfo.email()
        );

        return ApiResponse.success(response);
    }

    @GetMapping("/info")
    public ApiResponse<UserV1Dto.UserInfoResponse> getUserInfo(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        UserInfo userInfo = userFacade.getUserInfo(loginId, password);

        UserV1Dto.UserInfoResponse response = new UserV1Dto.UserInfoResponse(
            userInfo.loginId(),
            userInfo.getMaskedName(),
            userInfo.birthDate(),
            userInfo.email()
        );

        return ApiResponse.success(response);
    }

    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String currentPassword,
        @RequestBody UserV1Dto.UpdatePasswordRequest request
    ) {
        userFacade.updatePassword(loginId, currentPassword, request.newPassword());

        return ApiResponse.success(null);
    }

    @GetMapping("/me/likes")
    public ApiResponse<ProductV1Dto.PageResponse> getLikedProducts(
            @RequestHeader(value = "X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductInfo> products = userFacade.getLikedProducts(userId, pageable);
        return ApiResponse.success(ProductV1Dto.PageResponse.from(products));
    }
}
