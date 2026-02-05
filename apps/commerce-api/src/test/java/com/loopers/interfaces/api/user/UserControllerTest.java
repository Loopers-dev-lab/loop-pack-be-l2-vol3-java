package com.loopers.interfaces.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import com.loopers.domain.user.UserInfo;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    UserService userService;

    @DisplayName("회원가입 시,")
    @Nested
    class Signup {

        @Test
        void 성공하면_201_CREATED() throws Exception {
            // given
            UserDto.SignupRequest request = new UserDto.SignupRequest(
                    "looper123",
                    "password123",
                    "루퍼스",
                    LocalDate.of(1996, 11, 22),
                    "test@loopers.im"
            );

            when(userService.signup(request.loginId(), request.password(), request.name(), request.birthDate(), request.email()))
                    .thenReturn(LoginId.from("looper123"));

            String content = objectMapper.writeValueAsString(request);

            // when-then
            mockMvc.perform(post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(content))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.loginId").value("looper123"));
       }
    }

    @DisplayName("내 정보 조회 시,")
    @Nested
    class GetMyInfo {

        @Test
        void 성공하면_200_OK와_유저정보를_반환한다() throws Exception {
            // Arrange
            UserInfo userInfo = new UserInfo("loopers123", "루퍼*", LocalDate.of(1996, 11, 22), "test@loopers.im");
            when(userService.getMyInfo("loopers123", "loopers123!@")).thenReturn(userInfo);

            // Act & Assert
            mockMvc.perform(get("/api/users/me")
                            .header("X-Loopers-LoginId", "loopers123")
                            .header("X-Loopers-LoginPw", "loopers123!@"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loginId").value("loopers123"))
                    .andExpect(jsonPath("$.data.name").value("루퍼*"))
                    .andExpect(jsonPath("$.data.birthDate").value("1996-11-22"))
                    .andExpect(jsonPath("$.data.email").value("test@loopers.im"));
        }
    }

    @DisplayName("비밀번호 수정 시,")
    @Nested
    class ChangePassword {

        @Test
        void 성공하면_200_OK를_반환한다() throws Exception {
            // Arrange
            UserDto.ChangePasswordRequest request = new UserDto.ChangePasswordRequest("newPass1234!");
            doNothing().when(userService).changePassword("loopers123", "loopers123!@", "newPass1234!");

            String content = objectMapper.writeValueAsString(request);

            // Act & Assert
            mockMvc.perform(patch("/api/users/me/password")
                            .header("X-Loopers-LoginId", "loopers123")
                            .header("X-Loopers-LoginPw", "loopers123!@")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(content))
                    .andExpect(status().isOk());
        }
    }

}
