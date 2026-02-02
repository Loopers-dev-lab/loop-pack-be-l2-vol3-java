package com.loopers.interfaces.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UsersController {

    @PostMapping
    public ApiResponse<String> signUp(@RequestBody UserSignUpRequestDto requestDto) {
        return ApiResponse.success("ok");
    }
}
