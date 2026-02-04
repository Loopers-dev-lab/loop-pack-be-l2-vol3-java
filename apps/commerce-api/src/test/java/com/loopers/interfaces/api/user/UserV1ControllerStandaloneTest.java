package com.loopers.interfaces.api.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiControllerAdvice;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import com.loopers.domain.user.User;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserV1ControllerStandaloneTest {

    private MockMvc mockMvc;
    private UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        UserV1Controller controller = new UserV1Controller(userService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiControllerAdvice())
            .build();
    }

    @DisplayName("회원가입")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보로 가입하면, 회원 정보를 반환한다.")
        @Test
        void returns200_withValidInfo() throws Exception {
            // Arrange
            User savedUser = new User("testuser", "encrypted", "홍길동", "19900101", "test@example.com");
            when(userService.signUp("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"))
                .thenReturn(savedUser);

            // Act
            String json = mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"loginId":"testuser","password":"Test1234!","name":"홍길동","birthday":"19900101","email":"test@example.com"}
                    """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

            // Assert
            UserV1Dto.UserResponse response = objectMapper
                .readValue(json, new TypeReference<ApiResponse<UserV1Dto.UserResponse>>() {})
                .data();

            assertThat(response.loginId()).isEqualTo("testuser");
            assertThat(response.name()).isEqualTo("홍길동");
        }

        @DisplayName("중복된 loginId로 가입하면, 409를 반환한다.")
        @Test
        void returns409_whenDuplicateLoginId() throws Exception {
            // Arrange
            when(userService.signUp("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"))
                .thenThrow(new CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID입니다."));

            // Act & Assert
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"loginId":"testuser","password":"Test1234!","name":"홍길동","birthday":"19900101","email":"test@example.com"}
                    """))
                .andExpect(status().isConflict());
        }
    }

    @DisplayName("내정보 조회")
    @Nested
    class GetMyInfo {

        @DisplayName("헤더로 인증하면, 마스킹된 회원 정보를 반환한다.")
        @Test
        void returns200WithMaskedUserInfo() throws Exception {
            // Arrange
            when(userService.getMyInfo("testuser", "password1!"))
                .thenReturn(new UserInfo("testuser", "홍길*", "19900101", "test@example.com"));

            // Act
            String json = mockMvc.perform(get("/api/v1/users/me")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "password1!"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

            // Assert
            UserV1Dto.UserResponse response = objectMapper
                .readValue(json, new TypeReference<ApiResponse<UserV1Dto.UserResponse>>() {})
                .data();

            assertThat(response.loginId()).isEqualTo("testuser");
            assertThat(response.name()).isEqualTo("홍길*");
            assertThat(response.birthday()).isEqualTo("19900101");
            assertThat(response.email()).isEqualTo("test@example.com");
        }

        @DisplayName("존재하지 않는 회원이면, 404를 반환한다.")
        @Test
        void returns404_whenUserNotFound() throws Exception {
            // Arrange
            when(userService.getMyInfo("nouser", "password1!"))
                .thenThrow(new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

            // Act & Assert
            mockMvc.perform(get("/api/v1/users/me")
                    .header("X-Loopers-LoginId", "nouser")
                    .header("X-Loopers-LoginPw", "password1!"))
                .andExpect(status().isNotFound());
        }

        @DisplayName("비밀번호가 틀리면, 400을 반환한다.")
        @Test
        void returns400_whenPasswordIsWrong() throws Exception {
            // Arrange
            when(userService.getMyInfo("testuser", "wrongpw1!"))
                .thenThrow(new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다."));

            // Act & Assert
            mockMvc.perform(get("/api/v1/users/me")
                    .header("X-Loopers-LoginId", "testuser")
                    .header("X-Loopers-LoginPw", "wrongpw1!"))
                .andExpect(status().isBadRequest());
        }
    }
}
