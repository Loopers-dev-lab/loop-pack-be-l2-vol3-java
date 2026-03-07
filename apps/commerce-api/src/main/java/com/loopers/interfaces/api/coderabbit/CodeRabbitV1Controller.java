package com.loopers.interfaces.api.coderabbit;

import com.loopers.interfaces.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/code-rabbit")
public class CodeRabbitV1Controller implements CodeRabbitV1ApiSpec {

    private static final String PING_MESSAGE = "pong from code rabbit test api";
    private static final String HELLO_GREETING = "Hello";
    private static final String HELLO_TARGET = "CodeRabbit";
    private static final String STATUS_UP = "UP";
    private static final String API_VERSION = "1.0.0";

    @GetMapping("/ping")
    @Override
    public ApiResponse<CodeRabbitV1Dto.PingResponse> ping() {
        CodeRabbitV1Dto.PingResponse response = CodeRabbitV1Dto.PingResponse.of(
            PING_MESSAGE,
            System.currentTimeMillis()
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/hello")
    @Override
    public ApiResponse<CodeRabbitV1Dto.HelloResponse> hello() {
        CodeRabbitV1Dto.HelloResponse response = CodeRabbitV1Dto.HelloResponse.of(
            HELLO_GREETING,
            HELLO_TARGET
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/status")
    @Override
    public ApiResponse<CodeRabbitV1Dto.StatusResponse> status() {
        CodeRabbitV1Dto.StatusResponse response = CodeRabbitV1Dto.StatusResponse.of(
            STATUS_UP,
            API_VERSION
        );
        return ApiResponse.success(response);
    }
}
