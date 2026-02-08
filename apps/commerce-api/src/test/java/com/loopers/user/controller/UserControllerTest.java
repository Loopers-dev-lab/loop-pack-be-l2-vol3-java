package com.loopers.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.user.domain.User;
import com.loopers.user.dto.CreateUserRequest;
import com.loopers.user.exception.GlobalExceptionHandler;
import com.loopers.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
public class UserControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    UserService userService;

    @Test
    void 회원가입_성공_시_201_반환() throws Exception {
        //given
        CreateUserRequest request = new CreateUserRequest(
                "testId", "password123!", "김준영", "1990-04-27", "test@test.com"
        );

        User user = User.builder()
                .loginId("testId")
                .password("encoded")
                .name("김준영")
                .birthDate("1990-04-27")
                .email("test@test.com")
                .build();

        given(userService.createUser(any(CreateUserRequest.class)))
                .willReturn(user);

        //when
        ResultActions result = mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        );

        //then
        result.andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "{1} 누락 시 400 반환")
    @MethodSource("필수값_누락_케이스")
    void 필수값_누락_시_400_Bad_Request_반환(CreateUserRequest request, String fieldName) throws Exception {
        //given

        //when
        ResultActions result = mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        );

        //then
        result.andExpect(status().isBadRequest());
    }

    static Stream<Arguments> 필수값_누락_케이스() {
        return Stream.of(
                Arguments.of(new CreateUserRequest(null, "pw", "name", "1990-01-01", "a@a.com"), "loginId"),
                Arguments.of(new CreateUserRequest("test", null, "name", "1990-01-01", "a@a.com"), "password"),
                Arguments.of(new CreateUserRequest("test", "pw", null, "1990-01-01", "a@a.com"), "name"),
                Arguments.of(new CreateUserRequest("test", "pw", "name", null, "a@a.com"), "birthDate"),
                Arguments.of(new CreateUserRequest("test", "pw", "name", "1990-01-01", null), "email")
        );
    }

    @Test
    void 이메일_형식_오류_시_400_Bad_Request_반환() throws Exception {
        //given
        CreateUserRequest request = new CreateUserRequest(
                "testId", "password123!", "김준영", "1990-04-27", "testtest.com"
        );

        //when
        ResultActions result = mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        );

        //then
        result.andDo(print()).andExpect(status().isBadRequest());
    }

    @Test
    void 생년월일_형식_오류_시_400_Bad_Request_반환() throws Exception {
        //given
        CreateUserRequest request = new CreateUserRequest(
                "testId", "password123!", "김준영", "1990-0427", "test@test.com"
        );

        //when
        ResultActions result = mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        );

        //then
        result.andExpect(status().isBadRequest());
    }
}
