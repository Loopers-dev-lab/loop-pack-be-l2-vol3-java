package com.loopers.interfaces.api.user;

import com.loopers.application.queue.QueueFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.config.WebMvcConfig;
import com.loopers.interfaces.auth.AuthArgumentResolver;
import com.loopers.interfaces.auth.AuthInterceptor;
import com.loopers.interfaces.auth.EntryTokenInterceptor;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({AuthInterceptor.class, EntryTokenInterceptor.class, AuthArgumentResolver.class, WebMvcConfig.class})
@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    UserFacade userFacade;
    @MockitoBean
    QueueFacade queueFacade;

    @Test
    @DisplayName("회원가입 성공 시, 201 CREATED")
    void signup() throws Exception{
        // given
        UserDto.SignupRequest request = new UserDto.SignupRequest(
                "loginId",
                "password",
                "name",
                LocalDate.now(),
                "email"
        );

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.meta.result").value("SUCCESS"));

        verify(userFacade).signup(request.loginId(), request.password(), request.name(), request.birthDate(), request.email());
    }

    @Nested
    @DisplayName("GET /api/v1/users/me 요청 시, ")
    class GetMyInfo {

        @DisplayName("인증 헤더가 없으면, 401 을 반환한다.")
        @Test
        void returns401_whenHeadersMissing() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
        }

        @DisplayName("인증에 실패하면, 401 을 반환한다.")
        @Test
        void returns401_whenAuthFails() throws Exception {
            doThrow(new CoreException(ErrorType.UNAUTHORIZED))
                .when(userFacade).authenticate(any(), any());

            mockMvc.perform(get("/api/v1/users/me")
                .header("X-Loopers-LoginId", "testUser1")
                .header("X-Loopers-LoginPw", "wrongPassword"))
                .andExpect(status().isUnauthorized());
        }

        @DisplayName("인증에 성공하면, 200 과 내 정보를 반환한다.")
        @Test
        void returns200WithMyInfo_whenAuthSucceeds() throws Exception {
            UserInfo userInfo = new UserInfo("testUser1", "홍길*", LocalDate.of(1990, 1, 1), "test@loopers.im");
            when(userFacade.getMyInfo(any())).thenReturn(userInfo);

            mockMvc.perform(get("/api/v1/users/me")
                .header("X-Loopers-LoginId", "testUser1")
                .header("X-Loopers-LoginPw", "test1234!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value("testUser1"))
                .andExpect(jsonPath("$.data.name").value("홍길*"))
                .andExpect(jsonPath("$.data.birthDate").value("1990-01-01"))
                .andExpect(jsonPath("$.data.email").value("test@loopers.im"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/me/password")
    class ChangePassword {

        private static final String ENDPOINT = "/api/v1/users";

        @DisplayName("인증 헤더 없이 요청 시, 401 응답을 반환한다.")
        @Test
        void returnsUnauthorized_whenHeadersMissing() throws Exception {
            mockMvc.perform(put(ENDPOINT + "/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newPassword\":\"newPass1!\"}"))
                .andExpect(status().isUnauthorized());
        }

        @DisplayName("유효한 인증으로 요청 시, 200 응답을 반환한다.")
        @Test
        void returnsOk_whenRequestIsValid() throws Exception {
            mockMvc.perform(put(ENDPOINT + "/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newPassword\":\"newPass1!\"}")
                    .header("X-Loopers-LoginId", "testUser1")
                    .header("X-Loopers-LoginPw", "test1234!"))
                .andExpect(status().isOk());
        }
    }
}
