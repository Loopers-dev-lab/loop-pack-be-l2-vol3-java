package com.loopers.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.user.domain.User;
import com.loopers.user.dto.SignUpRequest;
import com.loopers.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
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
        SignUpRequest request = new SignUpRequest(
                "testId", "password123!", "김준영", "1990-04-27", "test@test.com"
        );

        User user = User.builder()
                .loginId("testId")
                .password("encoded")
                .name("김준영")
                .birthDate("1990-04-27")
                .email("test@test.com")
                .build();

        given(userService.signUp(anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(user);

        //when
        ResultActions result = mockMvc.perform(post("/api/v1/user/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        );

        //then
        result.andExpect(status().isCreated());
    }
}
