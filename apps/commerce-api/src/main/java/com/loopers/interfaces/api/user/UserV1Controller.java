package com.loopers.interfaces.api.user;

import com.loopers.domain.*;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserService userService;
    private final AuthenticationService authenticationService;

    @PostMapping("/signup")
    @Override
    public ApiResponse<UserV1Dto.SignupResponse> signup(
        @Valid @RequestBody UserV1Dto.SignupRequest request
    ) {
        LoginId loginId = new LoginId(request.loginId());
        Password password = new Password(request.password());
        Name name = new Name(request.name());
        BirthDate birthDate = new BirthDate(LocalDate.parse(request.birthDate(), BIRTH_DATE_FORMATTER));
        Email email = new Email(request.email());

        UserModel userModel = userService.signup(loginId, password, name, birthDate, email);
        UserV1Dto.SignupResponse response = UserV1Dto.SignupResponse.from(userModel);

        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password
    ) {
        UserModel authenticatedUser = authenticationService.authenticate(loginId, password);
        UserModel userInfo = userService.getMyInfo(authenticatedUser.getLoginId());
        UserV1Dto.MyInfoResponse response = UserV1Dto.MyInfoResponse.from(userInfo);

        return ApiResponse.success(response);
    }

    @PatchMapping("/password")
    @Override
    public ApiResponse<UserV1Dto.ChangePasswordResponse> changePassword(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String currentPasswordValue,
        @Valid @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        UserModel authenticatedUser = authenticationService.authenticate(loginId, currentPasswordValue);

        Password currentPassword = new Password(request.currentPassword());
        Password newPassword = new Password(request.newPassword());

        userService.changePassword(authenticatedUser.getLoginId(), currentPassword, newPassword);

        return ApiResponse.success(UserV1Dto.ChangePasswordResponse.success());
    }
}
