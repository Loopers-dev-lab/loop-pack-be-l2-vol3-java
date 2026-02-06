package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.dto.PasswordUpdateRequest;
import com.loopers.interfaces.api.dto.UserRegisterRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String BASE_URL = "/api/v1/users";
    private static final LocalDate TEST_BIRTHDAY = LocalDate.of(1990, 5, 15);

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("회원가입 API")
    class RegisterApi {

        @Test
        @DisplayName("회원가입 성공")
        void register_success() throws Exception {
            var request = new UserRegisterRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    TEST_BIRTHDAY,
                    "test@example.com"
            );

            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("중복 ID로 회원가입 시 실패")
        void register_fail_duplicateId() throws Exception {
            var request = new UserRegisterRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    TEST_BIRTHDAY,
                    "test@example.com"
            );

            // 첫 번째 가입
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // 동일 ID로 재가입 시도
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("필수 필드 누락 시 실패")
        void register_fail_missingFields() throws Exception {
            var request = new UserRegisterRequest(
                    "",
                    "Password1!",
                    "홍길동",
                    TEST_BIRTHDAY,
                    "test@example.com"
            );

            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("잘못된 이메일 형식으로 가입 시 실패")
        void register_fail_invalidEmail() throws Exception {
            var request = new UserRegisterRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    TEST_BIRTHDAY,
                    "invalid-email"
            );

            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("내 정보 조회 API")
    class GetMyInfoApi {

        @Test
        @DisplayName("내 정보 조회 성공")
        void getMyInfo_success() throws Exception {
            String loginId = "testuser1";
            String password = "Password1!";
            registerUser(loginId, password, "홍길동");

            mockMvc.perform(get(BASE_URL + "/me")
                            .header("X-Loopers-LoginId", loginId)
                            .header("X-Loopers-LoginPw", password))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.loginId").value(loginId))
                    .andExpect(jsonPath("$.name").value("홍길*"))
                    .andExpect(jsonPath("$.birthday").value("19900515"))
                    .andExpect(jsonPath("$.email").value("test@example.com"));
        }

        @Test
        @DisplayName("잘못된 비밀번호로 조회 시 실패")
        void getMyInfo_fail_wrongPassword() throws Exception {
            String loginId = "testuser1";
            registerUser(loginId, "Password1!", "홍길동");

            mockMvc.perform(get(BASE_URL + "/me")
                            .header("X-Loopers-LoginId", loginId)
                            .header("X-Loopers-LoginPw", "WrongPassword1!"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 실패")
        void getMyInfo_fail_userNotFound() throws Exception {
            mockMvc.perform(get(BASE_URL + "/me")
                            .header("X-Loopers-LoginId", "notexist")
                            .header("X-Loopers-LoginPw", "Password1!"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("2자 이름 마스킹 확인")
        void getMyInfo_maskedName_2chars() throws Exception {
            String loginId = "testuser1";
            String password = "Password1!";
            registerUser(loginId, password, "홍길");

            mockMvc.perform(get(BASE_URL + "/me")
                            .header("X-Loopers-LoginId", loginId)
                            .header("X-Loopers-LoginPw", password))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("홍*"));
        }
    }

    @Nested
    @DisplayName("비밀번호 변경 API")
    class UpdatePasswordApi {

        @Test
        @DisplayName("비밀번호 변경 성공")
        void updatePassword_success() throws Exception {
            String loginId = "testuser1";
            String currentPassword = "Password1!";
            String newPassword = "NewPassword1!";
            registerUser(loginId, currentPassword, "홍길동");

            var request = new PasswordUpdateRequest(currentPassword, newPassword);

            mockMvc.perform(put(BASE_URL + "/me/password")
                            .header("X-Loopers-LoginId", loginId)
                            .header("X-Loopers-LoginPw", currentPassword)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // 변경된 비밀번호로 조회 확인
            mockMvc.perform(get(BASE_URL + "/me")
                            .header("X-Loopers-LoginId", loginId)
                            .header("X-Loopers-LoginPw", newPassword))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("현재 비밀번호 불일치 시 실패")
        void updatePassword_fail_wrongCurrentPassword() throws Exception {
            String loginId = "testuser1";
            registerUser(loginId, "Password1!", "홍길동");

            var request = new PasswordUpdateRequest("WrongPassword1!", "NewPassword1!");

            mockMvc.perform(put(BASE_URL + "/me/password")
                            .header("X-Loopers-LoginId", loginId)
                            .header("X-Loopers-LoginPw", "Password1!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("동일한 비밀번호로 변경 시 실패")
        void updatePassword_fail_samePassword() throws Exception {
            String loginId = "testuser1";
            String password = "Password1!";
            registerUser(loginId, password, "홍길동");

            var request = new PasswordUpdateRequest(password, password);

            mockMvc.perform(put(BASE_URL + "/me/password")
                            .header("X-Loopers-LoginId", loginId)
                            .header("X-Loopers-LoginPw", password)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("인증 실패 시 비밀번호 변경 불가")
        void updatePassword_fail_authenticationFailed() throws Exception {
            String loginId = "testuser1";
            registerUser(loginId, "Password1!", "홍길동");

            var request = new PasswordUpdateRequest("Password1!", "NewPassword1!");

            mockMvc.perform(put(BASE_URL + "/me/password")
                            .header("X-Loopers-LoginId", loginId)
                            .header("X-Loopers-LoginPw", "WrongPassword1!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    private void registerUser(String loginId, String password, String name) throws Exception {
        var request = new UserRegisterRequest(
                loginId,
                password,
                name,
                TEST_BIRTHDAY,
                "test@example.com"
        );

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
