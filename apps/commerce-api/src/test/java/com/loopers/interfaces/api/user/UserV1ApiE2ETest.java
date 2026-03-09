package com.loopers.interfaces.api.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("User API V1 E2E 테스트")
class UserV1ApiE2ETest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // === 회원가입 ===

    @Nested
    @DisplayName("POST /api/v1/users - 회원가입")
    class RegisterTests {

        @Test
        @DisplayName("유효한 입력으로 200 반환")
        void POST_register_ShouldReturn200() throws Exception {
            var request = UserV1Dto.RegisterRequest.builder()
                    .loginId("testuser01")
                    .password("Test1234!@#")
                    .userName("홍길동")
                    .birthday("19900101")
                    .email("test@example.com")
                    .address("서울시 강남구")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loginId").value("testuser01"))
                    .andExpect(jsonPath("$.data.maskedName").value("홍길*"))
                    .andExpect(jsonPath("$.data.birthday").value("19900101"))
                    .andExpect(jsonPath("$.data.email").value("test@example.com"));
        }

        @Test
        @DisplayName("중복 loginId로 409 반환")
        void POST_register_DuplicateLoginId_ShouldReturn409() throws Exception {
            var request = UserV1Dto.RegisterRequest.builder()
                    .loginId("duplicate01")
                    .password("Test1234!@#")
                    .userName("홍길동")
                    .birthday("19900101")
                    .email("test@example.com")
                    .address("서울")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            var duplicateRequest = UserV1Dto.RegisterRequest.builder()
                    .loginId("duplicate01")
                    .password("Test1234!@#")
                    .userName("김철수")
                    .birthday("19910202")
                    .email("test2@example.com")
                    .address("부산")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(duplicateRequest)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("유효하지 않은 비밀번호로 400 반환")
        void POST_register_InvalidPassword_ShouldReturn400() throws Exception {
            var request = UserV1Dto.RegisterRequest.builder()
                    .loginId("testuser01")
                    .password("Short1!")
                    .userName("홍길동")
                    .birthday("19900101")
                    .email("test@example.com")
                    .address("서울")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("필수 필드 누락으로 400 반환")
        void POST_register_MissingRequiredFields_ShouldReturn400() throws Exception {
            var request = UserV1Dto.RegisterRequest.builder()
                    .loginId("testuser01")
                    .password("Test1234!@#")
                    // userName 누락
                    .birthday("19900101")
                    .email("test@example.com")
                    .build();

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // === 내 정보 조회 ===

    @Nested
    @DisplayName("GET /api/v1/users/me - 내 정보 조회")
    class GetMyInfoTests {

        @Test
        @DisplayName("인증 성공 시 200 반환")
        void GET_me_WithAuth_ShouldReturn200() throws Exception {
            registerUser("testuser01", "Test1234!@#", "홍길동");

            mockMvc.perform(get("/api/v1/users/me")
                            .header("X-Loopers-LoginId", "testuser01")
                            .header("X-Loopers-LoginPw", "Test1234!@#"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loginId").value("testuser01"))
                    .andExpect(jsonPath("$.data.maskedName").value("홍길*"));
        }

        @Test
        @DisplayName("인증 헤더 없이 400 반환")
        void GET_me_WithoutAuth_ShouldReturn400() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isBadRequest());
        }
    }

    // === 비밀번호 변경 ===

    @Nested
    @DisplayName("PATCH /api/v1/users/me/password - 비밀번호 변경")
    class ChangePasswordTests {

        @Test
        @DisplayName("올바른 비밀번호로 200 반환")
        void PATCH_changePassword_WithCorrectCurrentPw_ShouldReturn200() throws Exception {
            registerUser("testuser01", "Test1234!@#", "홍길동");

            var changeRequest = UserV1Dto.ChangePasswordRequest.builder()
                    .currentPassword("Test1234!@#")
                    .newPassword("NewPass5678$")
                    .build();

            mockMvc.perform(patch("/api/v1/users/me/password")
                            .header("X-Loopers-LoginId", "testuser01")
                            .header("X-Loopers-LoginPw", "Test1234!@#")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeRequest)))
                    .andExpect(status().isOk());

            // 새 비밀번호로 인증 가능한지 확인
            mockMvc.perform(get("/api/v1/users/me")
                            .header("X-Loopers-LoginId", "testuser01")
                            .header("X-Loopers-LoginPw", "NewPass5678$"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("잘못된 인증으로 401 반환")
        void PATCH_changePassword_WithWrongAuth_ShouldReturn401() throws Exception {
            registerUser("testuser01", "Test1234!@#", "홍길동");

            var changeRequest = UserV1Dto.ChangePasswordRequest.builder()
                    .currentPassword("Test1234!@#")
                    .newPassword("NewPass5678$")
                    .build();

            mockMvc.perform(patch("/api/v1/users/me/password")
                            .header("X-Loopers-LoginId", "testuser01")
                            .header("X-Loopers-LoginPw", "WrongPass123!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(changeRequest)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // === Helper ===

    private void registerUser(String loginId, String password, String userName) throws Exception {
        var request = UserV1Dto.RegisterRequest.builder()
                .loginId(loginId)
                .password(password)
                .userName(userName)
                .birthday("19900101")
                .email("test@example.com")
                .address("서울")
                .build();

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
