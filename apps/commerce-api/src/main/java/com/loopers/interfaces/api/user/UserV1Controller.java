package com.loopers.interfaces.api.user;

import com.loopers.domain.*;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final UserService userService;

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
}
